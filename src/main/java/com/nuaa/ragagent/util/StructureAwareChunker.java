package com.nuaa.ragagent.util;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 结构感知切分策略（STRUCTURE_AWARE）。
 *
 * <p>先用 commonmark + gfm-tables 把 Markdown 解析成 AST，再基于结构节点切分：
 * <ul>
 *   <li>Heading：每个 chunk 携带完整 headingPath（Markdown 标题链），不跨 headingPath 合并；headingPath 计入 token。</li>
 *   <li>Paragraph：完整段落为最小单元，同 headingPath 下合并至接近 targetSize。</li>
 *   <li>CodeBlock：连续代码块视为 CodeGroup，优先保持完整，可带 ≤contextMax 的邻接正文。</li>
 *   <li>Table：尽量整表完整，超限按完整 row 拆分，拆分后每个 chunk 重复 headingPath + 表头。</li>
 *   <li>List：尽量整列表完整，超限按完整 ListItem 拆分。</li>
 * </ul>
 * 只有单个 Paragraph / CodeBlock / Row / ListItem 不可再按结构拆且超 hardLimit 时，
 * 才用 chunkSize=targetSize、overlap=overlapSize 的兜底硬切。</p>
 *
 * @author jiyunhe
 */
@Component
@ConditionalOnProperty(name = "rag.chunk.strategy", havingValue = "STRUCTURE_AWARE")
public class StructureAwareChunker implements Chunker {

    /** YAML frontmatter 起始标记（---）。 */
    private static final String FRONT_MATTER_DELIMITER = "---";

    /** YAML frontmatter 结束标记（换行 + ---）。 */
    private static final String FRONT_MATTER_END = "\n---";

    /** Markdown 渲染中的换行符。 */
    private static final String NEWLINE = "\n";

    private final int targetSize;

    private final int hardLimit;

    private final int overlapSize;

    private final int contextMax;

    private final TokenCounter tokenCounter;

    private final Parser parser;

    public StructureAwareChunker(
            @Value("${rag.chunk.structure-aware.target-size:800}") int targetSize,
            @Value("${rag.chunk.structure-aware.hard-limit:1200}") int hardLimit,
            @Value("${rag.chunk.structure-aware.overlap-size:150}") int overlapSize,
            @Value("${rag.chunk.structure-aware.context-max:100}") int contextMax,
            TokenCounter tokenCounter) {
        this.targetSize = Math.max(100, targetSize);
        this.hardLimit = Math.max(this.targetSize, hardLimit);
        this.overlapSize = Math.max(0, Math.min(overlapSize, this.targetSize / 2));
        this.contextMax = Math.max(0, contextMax);
        this.tokenCounter = tokenCounter;
        this.parser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();
    }

    @Override
    public List<String> split(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        Node document = parser.parse(stripFrontMatter(text));
        List<Atom> atoms = buildAtoms(document);
        List<String> chunks = new ArrayList<>();
        process(atoms, chunks);
        return chunks;
    }

    // ---------------------------------------------------------------- AST 遍历

    /** 一个不可再按结构拆分的原子单元（除段落可合并外，其余按单元产出）。 */
    private static class Atom {

        enum Type { PARAGRAPH, CODE_GROUP, TABLE, LIST, OTHER }

        final Type type;
        final String headingPath;
        String text;
        int tokens;

        /** CODE_GROUP：组成代码块的渲染文本（保持文档顺序）。 */
        final List<String> codeBlockTexts = new ArrayList<>();

        /** TABLE：表格节点，用于按 row 拆分。 */
        TableBlock tableNode;

        /** LIST：列表节点，用于按 ListItem 拆分。 */
        ListBlock listNode;

        Atom(Type type, String headingPath, String text, int tokens) {
            this.type = type;
            this.headingPath = headingPath;
            this.text = text;
            this.tokens = tokens;
        }
    }

    private List<Atom> buildAtoms(Node doc) {
        List<Heading> stack = new ArrayList<>();
        List<Atom> atoms = new ArrayList<>();
        for (Node child = doc.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                while (!stack.isEmpty() && stack.get(stack.size() - 1).getLevel() >= heading.getLevel()) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(heading);
                continue;
            }
            String path = renderHeadingPath(stack);
            if (child instanceof Paragraph) {
                addAtom(atoms, path, new Atom(Atom.Type.PARAGRAPH, path, render(child), 0));
            } else if (child instanceof FencedCodeBlock || child instanceof IndentedCodeBlock) {
                String codeText = render(child);
                if (blank(codeText)) {
                    continue;
                }
                Atom last = atoms.isEmpty() ? null : atoms.get(atoms.size() - 1);
                if (last != null && last.type == Atom.Type.CODE_GROUP && last.headingPath.equals(path)) {
                    last.codeBlockTexts.add(codeText);
                    last.text = new StringBuilder(last.text).append("\n\n").append(codeText).toString();
                    last.tokens = tokenCounter.count(last.text);
                } else {
                    Atom atom = new Atom(Atom.Type.CODE_GROUP, path, codeText, 0);
                    atom.codeBlockTexts.add(codeText);
                    addAtom(atoms, path, atom);
                }
            } else if (child instanceof TableBlock table) {
                Atom atom = new Atom(Atom.Type.TABLE, path, render(child), 0);
                atom.tableNode = table;
                addAtom(atoms, path, atom);
            } else if (child instanceof ListBlock list) {
                Atom atom = new Atom(Atom.Type.LIST, path, render(child), 0);
                atom.listNode = list;
                addAtom(atoms, path, atom);
            } else if (child instanceof ThematicBreak) {
                // 无内容，跳过
            } else {
                addAtom(atoms, path, new Atom(Atom.Type.OTHER, path, render(child), 0));
            }
        }
        return atoms;
    }

    private void addAtom(List<Atom> atoms, String path, Atom atom) {
        if (blank(atom.text)) {
            return;
        }
        atom.tokens = tokenCounter.count(atom.text);
        atoms.add(atom);
    }

    // ---------------------------------------------------------------- 渲染

    private String render(Node node) {
        StringBuilder sb = new StringBuilder();
        renderTo(node, sb);
        return sb.toString();
    }

    private void renderTo(Node node, StringBuilder sb) {
        if (node instanceof Text t) {
            sb.append(t.getLiteral());
        } else if (node instanceof Code c) {
            sb.append('`').append(c.getLiteral()).append('`');
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            sb.append('\n');
        } else if (node instanceof ThematicBreak) {
            sb.append("\n---\n");
        } else if (node instanceof Emphasis) {
            sb.append('*');
            renderChildrenTo(node, sb);
            sb.append('*');
        } else if (node instanceof StrongEmphasis) {
            sb.append("**");
            renderChildrenTo(node, sb);
            sb.append("**");
        } else if (node instanceof Link link) {
            sb.append('[');
            renderChildrenTo(node, sb);
            sb.append(']');
            if (link.getDestination() != null && !link.getDestination().isEmpty()) {
                sb.append('(').append(link.getDestination()).append(')');
            }
        } else if (node instanceof Image) {
            renderChildrenTo(node, sb);
        } else if (node instanceof Paragraph || node instanceof Heading) {
            renderChildrenTo(node, sb);
        } else if (node instanceof FencedCodeBlock fcb) {
            String info = fcb.getInfo() == null ? "" : fcb.getInfo().trim();
            sb.append("```").append(info).append('\n');
            sb.append(fcb.getLiteral());
            if (!fcb.getLiteral().endsWith(NEWLINE)) {
                sb.append('\n');
            }
            sb.append("```");
        } else if (node instanceof IndentedCodeBlock icb) {
            sb.append(icb.getLiteral());
        } else if (node instanceof TableBlock table) {
            renderTableBlockTo(table, sb);
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            renderListTo((ListBlock) node, sb, 0);
        } else if (node instanceof BlockQuote) {
            sb.append("> ");
            renderChildrenTo(node, sb);
        } else if (node instanceof HtmlBlock hb) {
            sb.append(hb.getLiteral());
        } else {
            renderChildrenTo(node, sb);
        }
    }

    private void renderChildrenTo(Node node, StringBuilder sb) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            renderTo(child, sb);
        }
    }

    /** 渲染 headingPath 为 Markdown 标题链，如 "## 配置\n### 设置（settings）"。 */
    private String renderHeadingPath(List<Heading> stack) {
        if (stack.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Heading h : stack) {
            sb.append("#".repeat(h.getLevel())).append(' ');
            renderTo(h, sb);
            sb.append('\n');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void renderTableBlockTo(TableBlock table, StringBuilder sb) {
        renderTableHeadTo(table, sb);
        for (Node child = table.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableBody body) {
                for (Node rowNode = body.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                    if (rowNode instanceof TableRow row) {
                        renderTableRowTo(row, sb);
                        sb.append('\n');
                    }
                }
            }
        }
    }

    /** 表头行 + 分隔行（如 "| 别名 | 类型 |\n| --- | --- |"）。 */
    private String renderTableHeader(TableBlock table) {
        StringBuilder sb = new StringBuilder();
        renderTableHeadTo(table, sb);
        return sb.toString();
    }

    private void renderTableHeadTo(TableBlock table, StringBuilder sb) {
        for (Node child = table.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableHead head) {
                Node rowNode = head.getFirstChild();
                if (rowNode instanceof TableRow headRow) {
                    renderTableRowTo(headRow, sb);
                    sb.append('\n');
                    int cols = 0;
                    for (Node cell = headRow.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (cell instanceof TableCell) {
                            cols++;
                        }
                    }
                    sb.append('|');
                    for (int i = 0; i < cols; i++) {
                        sb.append(" --- |");
                    }
                    sb.append('\n');
                }
            }
        }
    }

    private void renderTableRowTo(TableRow row, StringBuilder sb) {
        sb.append('|');
        for (Node child = row.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableCell cell) {
                sb.append(' ');
                renderTo(cell, sb);
                sb.append(" |");
            }
        }
    }

    private List<String> collectTableRows(TableBlock table) {
        List<String> rows = new ArrayList<>();
        for (Node child = table.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableBody body) {
                for (Node rowNode = body.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                    if (rowNode instanceof TableRow row) {
                        StringBuilder sb = new StringBuilder();
                        renderTableRowTo(row, sb);
                        rows.add(sb.toString());
                    }
                }
            }
        }
        return rows;
    }

    private void renderListTo(ListBlock list, StringBuilder sb, int depth) {
        int index = 1;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem item)) {
                continue;
            }
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            if (list instanceof OrderedList) {
                sb.append(index++).append(". ");
            } else {
                sb.append("- ");
            }
            renderListItemContentTo(item, sb, depth);
            sb.append('\n');
        }
    }

    private void renderListItemContentTo(ListItem item, StringBuilder sb, int depth) {
        boolean started = false;
        for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListBlock nested) {
                sb.append('\n');
                renderListTo(nested, sb, depth + 1);
            } else {
                if (started) {
                    sb.append(' ');
                }
                renderTo(child, sb);
                started = true;
            }
        }
    }

    private List<String> collectListItems(ListBlock list) {
        List<String> items = new ArrayList<>();
        collectListItemsTo(list, items, 0);
        return items;
    }

    private void collectListItemsTo(ListBlock list, List<String> out, int depth) {
        int index = 1;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem item)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            if (list instanceof OrderedList) {
                sb.append(index++).append(". ");
            } else {
                sb.append("- ");
            }
            renderListItemContentTo(item, sb, depth);
            out.add(sb.toString().trim());
        }
    }

    // ---------------------------------------------------------------- 切分

    private void process(List<Atom> atoms, List<String> chunks) {
        if (atoms.isEmpty()) {
            return;
        }
        SplitState st = new SplitState();
        st.currentPath = atoms.get(0).headingPath;
        st.headTokens = tokenCounter.count(st.currentPath);
        while (st.i < atoms.size()) {
            handleAtom(atoms, chunks, st);
        }
        flushParagraphs(st.pending, st.currentPath, chunks);
    }

    /** 切分循环的可变状态，集中传给各辅助方法，避免长参数列表。 */
    private static class SplitState {
        final List<Atom> pending = new ArrayList<>();
        int pendingTokens;
        String currentPath;
        int headTokens;
        /** 桥：上一个特殊块消费的 next，供下一个特殊块强行借作 prev；每次借后失效，跨章节/遇正文失效。 */
        final AtomicReference<Atom> bridge = new AtomicReference<>();
        int i;
    }

    /** 处理单个 atom：必要时切到新章节并重置状态，再按类型分发。 */
    private void handleAtom(List<Atom> atoms, List<String> chunks, SplitState st) {
        Atom atom = atoms.get(st.i);
        if (!atom.headingPath.equals(st.currentPath)) {
            flushParagraphs(st.pending, st.currentPath, chunks);
            st.pending.clear();
            st.pendingTokens = 0;
            st.currentPath = atom.headingPath;
            st.headTokens = tokenCounter.count(st.currentPath);
            st.bridge.set(null);
        }
        switch (atom.type) {
            case PARAGRAPH -> handleParagraph(atom, chunks, st);
            case CODE_GROUP -> handleCodeGroup(atom, atoms, chunks, st);
            case TABLE -> handleTable(atom, atoms, chunks, st);
            case LIST -> handleList(atom, chunks, st);
            case OTHER -> handleOther(atom, chunks, st);
            default -> {
                // 阿里规约要求 switch 必须包含 default；枚举穷尽后新增取值从此处兜底，避免静默跳过。
            }
        }
    }

    private void handleParagraph(Atom atom, List<String> chunks, SplitState st) {
        // 普通段落进入 pending，等待特殊块借用或随正文 chunk 输出；同时使桥失效（不再是紧邻）。
        st.bridge.set(null);
        if (st.pending.isEmpty() && atom.tokens > targetSize) {
            if (atom.tokens <= hardLimit) {
                emitChunk(st.currentPath, atom.text, chunks);
            } else {
                hardSplitChunks(st.currentPath, atom.text, chunks);
            }
            st.i++;
        } else if (!st.pending.isEmpty() && st.headTokens + st.pendingTokens + atom.tokens > targetSize) {
            flushParagraphs(st.pending, st.currentPath, chunks);
            st.pending.clear();
            st.pendingTokens = 0;
            st.pending.add(atom);
            st.pendingTokens = atom.tokens;
            st.i++;
        } else {
            st.pending.add(atom);
            st.pendingTokens += atom.tokens;
            st.i++;
        }
    }

    /** 只有结构装得下（hardLimit - core）才借前置段落；装不下则留给正文。 */
    private void handleCodeGroup(Atom atom, List<Atom> atoms, List<String> chunks, SplitState st) {
        Atom prev = borrowPrevParagraph(st.pending, hardLimit - atom.tokens, st.bridge);
        flushParagraphs(st.pending, st.currentPath, chunks);
        st.pending.clear();
        st.pendingTokens = 0;
        Atom next = nextShortParagraph(atoms, st.i);
        st.i += emitCodeGroupChunk(st.currentPath, atom, prev, next, st.bridge, chunks);
    }

    private void handleTable(Atom atom, List<Atom> atoms, List<String> chunks, SplitState st) {
        Atom prev = borrowPrevParagraph(st.pending, hardLimit - atom.tokens, st.bridge);
        flushParagraphs(st.pending, st.currentPath, chunks);
        st.pending.clear();
        st.pendingTokens = 0;
        Atom next = nextShortParagraph(atoms, st.i);
        st.i += emitTableChunk(st.currentPath, atom, prev, next, st.bridge, chunks);
    }

    private void handleList(Atom atom, List<String> chunks, SplitState st) {
        Atom prev = borrowPrevParagraph(st.pending, hardLimit - atom.tokens, st.bridge);
        flushParagraphs(st.pending, st.currentPath, chunks);
        st.pending.clear();
        st.pendingTokens = 0;
        emitListChunk(st.currentPath, atom, prev, chunks);
        st.i++;
    }

    private void handleOther(Atom atom, List<String> chunks, SplitState st) {
        flushParagraphs(st.pending, st.currentPath, chunks);
        st.pending.clear();
        st.pendingTokens = 0;
        emitChunk(st.currentPath, atom.text, chunks);
        st.i++;
    }

    /**
     * 为代码块/表格/列表借前置上下文段落。先失效旧桥，再判断：
     * 1) 上一个特殊块消费的 next（桥段落）——≤contextMax 且放得下则强行借用（特殊块间可重复）；
     * 2) pending 尾部——≤contextMax 且放得下则借用。
     * 借前先判断结构装得下才借；装不下则不借，段落留在 pending，随正文输出（特殊块消费不了就留给正文）。
     *
     * @param headroom 结构装下前置段落后剩余可用的 token 数（hardLimit - core）
     */
    private Atom borrowPrevParagraph(List<Atom> pending, int headroom, AtomicReference<Atom> bridge) {
        Atom candidate = bridge.get();
        // 桥一次性：无论是否借用，处理完当前块后失效
        bridge.set(null);
        if (candidate != null && candidate.tokens <= contextMax && candidate.tokens <= headroom) {
            return candidate;
        }
        if (!pending.isEmpty()) {
            Atom last = pending.get(pending.size() - 1);
            if (last.tokens <= contextMax && last.tokens <= headroom) {
                pending.remove(pending.size() - 1);
                return last;
            }
        }
        return null;
    }

    /** 紧邻 index 之后、同章节且 ≤contextMax 的段落，作为结构单元的后置上下文；否则返回 null。 */
    private Atom nextShortParagraph(List<Atom> atoms, int index) {
        if (index + 1 < atoms.size()) {
            Atom next = atoms.get(index + 1);
            if (next.type == Atom.Type.PARAGRAPH && next.tokens <= contextMax
                    && next.headingPath.equals(atoms.get(index).headingPath)) {
                return next;
            }
        }
        return null;
    }

    private void flushParagraphs(List<Atom> pending, String path, List<String> chunks) {
        if (pending.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (Atom a : pending) {
            if (body.length() > 0) {
                body.append("\n\n");
            }
            body.append(a.text);
        }
        emitChunk(path, body.toString(), chunks);
    }

    private void emitChunk(String path, String body, List<String> chunks) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        String trimmed = body.trim();
        chunks.add(path.isEmpty() ? trimmed : path + "\n\n" + trimmed);
    }

    /** 单个结构单元超 hardLimit 时的兜底硬切：targetSize 切分 + overlap 重叠。 */
    private void hardSplitChunks(String path, String text, List<String> chunks) {
        for (String part : tokenCounter.splitByTokens(text, targetSize, overlapSize)) {
            emitChunk(path, part, chunks);
        }
    }

    private void emitJoined(String path, Atom prev, String core, Atom next, List<String> chunks) {
        StringBuilder body = new StringBuilder();
        if (prev != null) {
            body.append(prev.text);
            body.append("\n\n");
        }
        body.append(core);
        if (next != null) {
            body.append("\n\n").append(next.text);
        }
        emitChunk(path, body.toString(), chunks);
    }

    // ---- CodeGroup ----

    /**
     * 处理 CodeGroup。结构装得下则带上 prev，next 放得下也带上（并设桥供下一个特殊块复用）；
     * 本身超 hardLimit 则不消费任何前后段落，直接按结构拆分。返回实际消费的 atom 数（是否含 next）。
     */
    private int emitCodeGroupChunk(String path, Atom group, Atom prev, Atom next,
                                   AtomicReference<Atom> bridge, List<String> chunks) {
        int core = group.tokens;
        if (core > hardLimit) {
            if (group.codeBlockTexts.size() > 1) {
                splitCodeGroupByBlocks(path, group, chunks);
            } else {
                hardSplitChunks(path, group.text, chunks);
            }
            return 1;
        }
        int prevT = prev == null ? 0 : prev.tokens;
        boolean useNext = next != null && core + prevT + next.tokens <= hardLimit;
        if (useNext) {
            // 消费作 next，供下一个特殊块强行借作 prev
            bridge.set(next);
            emitJoined(path, prev, group.text, next, chunks);
            return 2;
        }
        emitJoined(path, prev, group.text, null, chunks);
        return 1;
    }

    /** CodeGroup 超限时，在完整代码块边界拆分，贪心凑近 targetSize。 */
    private void splitCodeGroupByBlocks(String path, Atom group, List<String> chunks) {
        List<String> current = new ArrayList<>();
        int curTokens = 0;
        for (String blockText : group.codeBlockTexts) {
            int bt = tokenCounter.count(blockText);
            if (bt > hardLimit) {
                if (!current.isEmpty()) {
                    emitChunk(path, String.join("\n\n", current), chunks);
                    current.clear();
                    curTokens = 0;
                }
                hardSplitChunks(path, blockText, chunks);
                continue;
            }
            if (curTokens > 0 && curTokens + bt > targetSize) {
                emitChunk(path, String.join("\n\n", current), chunks);
                current.clear();
                curTokens = 0;
            }
            current.add(blockText);
            curTokens += bt;
        }
        if (!current.isEmpty()) {
            emitChunk(path, String.join("\n\n", current), chunks);
        }
    }

    // ---- Table ----

    /**
     * 处理 Table。结构装得下则带上 prev，next 放得下也带上（并设桥供下一个特殊块复用）；
     * 本身超 hardLimit 则不消费任何前后段落，直接按完整 row 拆分。返回实际消费的 atom 数（是否含 next）。
     */
    private int emitTableChunk(String path, Atom table, Atom prev, Atom next,
                               AtomicReference<Atom> bridge, List<String> chunks) {
        int core = table.tokens;
        if (core > hardLimit) {
            splitTableByRows(path, table, chunks);
            return 1;
        }
        int prevT = prev == null ? 0 : prev.tokens;
        boolean useNext = next != null && core + prevT + next.tokens <= hardLimit;
        if (useNext) {
            // 消费作 next，供下一个特殊块强行借作 prev
            bridge.set(next);
            emitJoined(path, prev, table.text, next, chunks);
            return 2;
        }
        emitJoined(path, prev, table.text, null, chunks);
        return 1;
    }

    /** 表格超限时按完整 row 拆分，每个拆分 chunk 重复 headingPath + 表头。 */
    private void splitTableByRows(String path, Atom table, List<String> chunks) {
        TableBlock node = table.tableNode;
        String header = renderTableHeader(node);
        int headerTokens = tokenCounter.count(header);
        int headTokens = tokenCounter.count(path);
        List<String> rows = collectTableRows(node);

        List<String> current = new ArrayList<>();
        int curTokens = 0;
        for (String rowText : rows) {
            int rt = tokenCounter.count(rowText);
            if (rt > hardLimit) {
                if (!current.isEmpty()) {
                    emitTableRowChunk(path, header, current, chunks);
                    current.clear();
                    curTokens = 0;
                }
                hardSplitChunks(path, rowText, chunks);
                continue;
            }
            if (curTokens > 0 && headTokens + headerTokens + curTokens + rt > targetSize) {
                emitTableRowChunk(path, header, current, chunks);
                current.clear();
                curTokens = 0;
            }
            current.add(rowText);
            curTokens += rt;
        }
        if (!current.isEmpty()) {
            emitTableRowChunk(path, header, current, chunks);
        }
    }

    private void emitTableRowChunk(String path, String header, List<String> rows, List<String> chunks) {
        StringBuilder body = new StringBuilder(header);
        for (String row : rows) {
            body.append('\n').append(row);
        }
        emitChunk(path, body.toString(), chunks);
    }

    // ---- List ----

    /** 处理 List。结构装得下则带上 prev；本身超 hardLimit 则不消费前置段落，直接按 ListItem 拆分。返回实际消费的 atom 数。 */
    private int emitListChunk(String path, Atom list, Atom prev, List<String> chunks) {
        int core = list.tokens;
        if (core > hardLimit) {
            splitListByItems(path, list, chunks);
            return 1;
        }
        emitJoined(path, prev, list.text, null, chunks);
        return 1;
    }

    /** 列表超限时按完整 ListItem 拆分，贪心凑近 targetSize；单个 ListItem 超限才兜底硬切。 */
    private void splitListByItems(String path, Atom list, List<String> chunks) {
        List<String> current = new ArrayList<>();
        int curTokens = 0;
        for (String itemText : collectListItems(list.listNode)) {
            int it = tokenCounter.count(itemText);
            if (it > hardLimit) {
                if (!current.isEmpty()) {
                    emitChunk(path, String.join("\n", current), chunks);
                    current.clear();
                    curTokens = 0;
                }
                hardSplitChunks(path, itemText, chunks);
                continue;
            }
            if (curTokens > 0 && curTokens + it > targetSize) {
                emitChunk(path, String.join("\n", current), chunks);
                current.clear();
                curTokens = 0;
            }
            current.add(itemText);
            curTokens += it;
        }
        if (!current.isEmpty()) {
            emitChunk(path, String.join("\n", current), chunks);
        }
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** 去掉 YAML frontmatter（首尾 --- 包围块），只留正文。 */
    private String stripFrontMatter(String source) {
        if (source.startsWith(FRONT_MATTER_DELIMITER)) {
            int end = source.indexOf(FRONT_MATTER_END, 3);
            if (end >= 0) {
                return source.substring(end + FRONT_MATTER_END.length());
            }
        }
        return source;
    }
}
