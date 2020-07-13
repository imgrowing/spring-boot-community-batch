package com.community.batch.jobs.partitioner;

import com.community.batch.domain.enums.Grade;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class InactiveUserPartitioner implements Partitioner {

	public static final String KEY_GRADE = "grade";
	public static final String PARTITION_KEY = "partition";

	@Override
	public Map<String, ExecutionContext> partition(int gridSize) {
		Map<String, ExecutionContext> map = new HashMap<>(gridSize);
		Grade[] grades = Grade.values(); // Grade는 총 3개 이다.

		for (int i = 0; i < grades.length; i++) { // gridSize와는 무관하게 돌아간다.
			ExecutionContext executionContext = new ExecutionContext();
			executionContext.put(KEY_GRADE, grades[i].name());
			map.put(PARTITION_KEY + i, executionContext);
		}

		return map;
	}
}
