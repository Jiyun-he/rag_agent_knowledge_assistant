package com.nuaa.ragagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nuaa.ragagent.entity.KbDocument;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
/**
 * @author jiyunhe
 */

public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 以锁定读（当前读）获取文档状态，并持有该行共享锁至事务提交。
     *
     * <p>普通 SELECT 在 REPEATABLE READ 事务中命中快照，看不到并发事务已提交的修改；
     * 索引构建需要在收尾前确认文档是否已被并发删除/更新，因此必须用当前读。
     * 由于读出后会持锁至提交，只应在确实需要“确认后立即写入”的收尾阶段调用。</p>
     *
     * @param id 文档 ID
     * @return 文档状态值；文档不存在时返回 null
     */
    @Select("SELECT status FROM kb_document WHERE id = #{id} FOR SHARE")
    Integer selectStatusForShare(@Param("id") Long id);
}
