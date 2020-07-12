package com.community.batch.jobs.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InactiveChunkListener implements ChunkListener {
	@Override
	public void beforeChunk(ChunkContext context) {
		log.warn("Before Chunk");
	}

	@Override
	public void afterChunk(ChunkContext context) {
		log.warn("After Chunk");
	}

	@Override
	public void afterChunkError(ChunkContext context) {
		log.warn("After Chunk Error");
	}
}
