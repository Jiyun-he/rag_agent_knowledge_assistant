package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.request.UpdateSpaceRequest;
import com.nuaa.ragagent.service.KnowledgeSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeSpaceServiceImpl implements KnowledgeSpaceService {

    private final KbSpaceMapper kbSpaceMapper;

    private final KbDocumentMapper kbDocumentMapper;

    public KnowledgeSpaceServiceImpl(KbSpaceMapper kbSpaceMapper,
                                     KbDocumentMapper kbDocumentMapper) {
        this.kbSpaceMapper = kbSpaceMapper;
        this.kbDocumentMapper = kbDocumentMapper;
    }

    @Override
    public KbSpace create(CreateSpaceRequest request) {
        Long sameNameCount = kbSpaceMapper.selectCount(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getName, request.getName())
                        .eq(KbSpace::getStatus, 1)
        );

        if (sameNameCount > 0) {
            throw new BusinessException("Space name already exists");
        }

        KbSpace space = new KbSpace()
                .setName(request.getName())
                .setDescription(request.getDescription())
                .setStatus(1);

        kbSpaceMapper.insert(space);
        return space;
    }

    @Override
    public List<KbSpace> list() {
        return kbSpaceMapper.selectList(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getStatus, 1)
                        .orderByDesc(KbSpace::getId)
        );
    }

    @Override
    public KbSpace getById(Long id) {
        KbSpace space = kbSpaceMapper.selectOne(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getId, id)
                        .eq(KbSpace::getStatus, 1)
        );

        if (space == null) {
            throw new BusinessException("Space not found");
        }

        return space;
    }

    @Override
    public KbSpace update(Long id, UpdateSpaceRequest request) {
        KbSpace existingSpace = getById(id);

        Long sameNameCount = kbSpaceMapper.selectCount(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getName, request.getName())
                        .eq(KbSpace::getStatus, 1)
                        .ne(KbSpace::getId, id)
        );

        if (sameNameCount > 0) {
            throw new BusinessException("Space name already exists");
        }

        existingSpace
                .setName(request.getName())
                .setDescription(request.getDescription());

        kbSpaceMapper.updateById(existingSpace);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(Long id) {
        getById(id);

        Long activeDocumentCount = kbDocumentMapper.selectCount(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getSpaceId, id)
                        .eq(KbDocument::getStatus, 1)
        );

        if (activeDocumentCount > 0) {
            throw new BusinessException("Space contains active documents");
        }

        kbSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<KbSpace>()
                        .eq(KbSpace::getId, id)
                        .set(KbSpace::getStatus, 0)
        );

        return true;
    }
}