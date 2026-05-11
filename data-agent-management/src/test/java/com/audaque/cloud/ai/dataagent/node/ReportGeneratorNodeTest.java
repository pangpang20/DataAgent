/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.audaque.cloud.ai.dataagent.node;

import com.audaque.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.audaque.cloud.ai.dataagent.dto.planner.Plan;
import com.audaque.cloud.ai.dataagent.workflow.node.ReportGeneratorNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorNodeTest {

	private ReportGeneratorNode node;

	private Method buildAnalysisStepsAndDataMethod;

	@BeforeEach
	void setUp() throws Exception {
		// We only need to test the private buildAnalysisStepsAndData method
		// Create node with null dependencies (not used by the tested method)
		node = new ReportGeneratorNode(null, null, null);

		buildAnalysisStepsAndDataMethod = ReportGeneratorNode.class.getDeclaredMethod(
				"buildAnalysisStepsAndData", Plan.class, HashMap.class, String.class);
		buildAnalysisStepsAndDataMethod.setAccessible(true);
	}

	private String invokeBuildAnalysisStepsAndData(Plan plan, HashMap<String, String> executionResults,
			String pythonExecuteResult) throws Exception {
		return (String) buildAnalysisStepsAndDataMethod.invoke(node, plan, executionResults, pythonExecuteResult);
	}

	private Plan createSimplePlan() {
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse("SQL_GENERATE_NODE");
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction("查询数据");
		step.setToolParameters(params);

		Plan plan = new Plan();
		plan.setThoughtProcess("分析需求");
		plan.setExecutionPlan(List.of(step));
		return plan;
	}

	@Test
	@DisplayName("Chart image URL should be used directly in markdown")
	void chartImageUrl_shouldBeUsedDirectly() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String imageUrl = "/api/upload/data-agent/charts/chart_abc123.png";
		String pythonResult = String.format("{\"chart_image\":\"%s\",\"summary\":\"分析完成\"}", imageUrl);

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertTrue(result.contains("![分析图表](" + imageUrl + ")"),
				"Should use URL directly without data URI prefix");
		assertFalse(result.contains("data:image/png;base64,"), "Should not contain base64 prefix");
		assertTrue(result.contains("分析完成"), "Should contain summary");
	}

	@Test
	@DisplayName("Chart image HTTP URL should be used directly in markdown")
	void chartImageHttpUrl_shouldBeUsedDirectly() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String imageUrl = "https://oss.example.com/data-agent/charts/chart.png";
		String pythonResult = String.format("{\"chart_image\":\"%s\"}", imageUrl);

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertTrue(result.contains("![分析图表](" + imageUrl + ")"),
				"Should use HTTP URL directly");
		assertFalse(result.contains("data:image/png;base64,"), "Should not contain base64 prefix");
	}

	@Test
	@DisplayName("Base64 chart image should use data URI prefix (fallback)")
	void base64ChartImage_shouldUseDataUriPrefix() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ";
		String pythonResult = String.format("{\"chart_image\":\"%s\"}", base64Image);

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertTrue(result.contains("![分析图表](data:image/png;base64," + base64Image + ")"),
				"Should use data URI prefix for base64");
	}

	@Test
	@DisplayName("No chart image should not produce image markdown")
	void noChartImage_shouldNotProduceImageMarkdown() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String pythonResult = "{\"summary\":\"分析完成\"}";

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertFalse(result.contains("![分析图表]"), "Should not contain image markdown");
		assertTrue(result.contains("分析完成"), "Should contain summary");
	}

	@Test
	@DisplayName("Empty python result should show no data message")
	void emptyPythonResult_shouldShowNoData() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, "");

		assertTrue(result.contains("暂无执行结果数据"), "Should show no data message");
	}

	@Test
	@DisplayName("Null chart image should be skipped")
	void nullChartImage_shouldBeSkipped() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String pythonResult = "{\"chart_image\":null,\"summary\":\"test\"}";

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertFalse(result.contains("![分析图表]"), "Null chart image should be skipped");
	}

	@Test
	@DisplayName("String 'null' chart image should be skipped")
	void stringNullChartImage_shouldBeSkipped() throws Exception {
		Plan plan = createSimplePlan();
		HashMap<String, String> executionResults = new HashMap<>();
		String pythonResult = "{\"chart_image\":\"null\",\"summary\":\"test\"}";

		String result = invokeBuildAnalysisStepsAndData(plan, executionResults, pythonResult);

		assertFalse(result.contains("![分析图表]"), "String 'null' chart image should be skipped");
	}

}
