package com.example.Mutual_Followers;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import java.util.*;

@SpringBootApplication
public class Mutual_Followers {

	public static void main(String[] args) {
		SpringApplication.run(Mutual_Followers.class, args);
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	@Bean
	CommandLineRunner run(RestTemplate restTemplate) {
		return args -> {
			// 1. Call /generateWebhook
			String generateWebhookUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			String requestBody = "{\"name\": \"John Doe\", \"regNo\": \"REG12347\", \"email\": \"john@example.com\"}";
			HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

			ResponseEntity<Map> response = restTemplate.postForEntity(generateWebhookUrl, request, Map.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				Map<String, Object> responseBody = response.getBody();
				if (responseBody != null) {
					String webhookUrl = (String) responseBody.get("webhook");
					String accessToken = (String) responseBody.get("accessToken");
					Map<String, Object> data = (Map<String, Object>) responseBody.get("data");

					// 2. Solve the problem
					Map<String, Object> result = solveProblem(data, "REG12347"); // Pass regNo

					// 3. Send the result to the webhook with retry
					sendResultWithRetry(restTemplate, webhookUrl, accessToken, result);
				}
			} else {
				System.err.println("Failed to generate webhook: " + response.getStatusCode());
			}
			// *****************************************************************************************
			// Add this section to test your solveProblem method:
			// Test Case:  (Same example from the problem description)
			Map<String, Object> testData = new HashMap<>();
			List<Map<String, Object>> users = new ArrayList<>();
			users.add(Map.of("id", 1, "name", "Alice", "follows", List.of(2, 3)));
			users.add(Map.of("id", 2, "name", "Bob", "follows", List.of(1)));
			users.add(Map.of("id", 3, "name", "Charlie", "follows", List.of(4)));
			users.add(Map.of("id", 4, "name", "David", "follows", List.of(3)));
			testData.put("users", users);

			Map<String, Object> expectedResult = new HashMap<>();
			expectedResult.put("regNo", "REG12347");
			expectedResult.put("outcome", List.of(List.of(1, 2), List.of(3, 4)));

			Map<String, Object> actualResult = solveProblem(testData, "REG12347");

			System.out.println("Test Case Input: " + testData);
			System.out.println("Expected Result: " + expectedResult);
			System.out.println("Actual Result:   " + actualResult);

			if (actualResult.equals(expectedResult)) {
				System.out.println("Test Case Passed!");
			} else {
				System.out.println("Test Case Failed!");
			}
			// *****************************************************************************************
		};
	}

	private Map<String, Object> solveProblem(Map<String, Object> data, String regNo) {
		List<List<Integer>> outcome = new ArrayList<>();
		if (data != null && data.containsKey("users")) {
			// Corrected way to get the users data.
			Object usersData = data.get("users");

			if (usersData instanceof List) {
				List<Map<String, Object>> users = (List<Map<String, Object>>) usersData;
				for (int i = 0; i < users.size(); i++) {
					Map<String, Object> user1 = users.get(i);
					Integer id1 = (Integer) user1.get("id");
					List<Integer> follows1 = (List<Integer>) user1.get("follows");

					for (int j = i + 1; j < users.size(); j++) {
						Map<String, Object> user2 = users.get(j);
						Integer id2 = (Integer) user2.get("id");
						List<Integer> follows2 = (List<Integer>) user2.get("follows");

						if (follows1 != null && follows2 != null && follows1.contains(id2) && follows2.contains(id1)) {
							outcome.add(Arrays.asList(Math.min(id1, id2), Math.max(id1, id2)));
						}
					}
				}
			} else {
				System.err.println("Error: 'users' is not a List: " + usersData.getClass().getName());
				// Handle the error appropriately, e.g., return an empty result or throw an exception
				return Collections.emptyMap();
			}
		}
		Map<String, Object> result = new HashMap<>();
		result.put("regNo", regNo);
		result.put("outcome", outcome);
		return result;
	}

	private void sendResultWithRetry(RestTemplate restTemplate, String webhookUrl, String accessToken, Map<String, Object> result) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", accessToken);
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(result, headers);

		int retryCount = 0;
		final int maxRetries = 4;
		while (retryCount < maxRetries) {
			try {
				ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
				if (response.getStatusCode() == HttpStatus.OK) {
					System.out.println("Successfully sent result to webhook.");
					return; // Exit the method on success
				} else {
					System.err.println("Failed to send result. Status code: " + response.getStatusCode());
				}
			} catch (RestClientException e) {
				System.err.println("Error sending result: " + e.getMessage());
			}
			retryCount++;
			if (retryCount < maxRetries) {
				try {
					Thread.sleep(1000); // Sleep for 1 second before retrying
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt(); // Restore the interrupted status
					System.err.println("Thread interrupted while waiting to retry: " + e.getMessage());
					return; // Exit if interrupted
				}
			}
		}
		System.err.println("Failed to send result after " + maxRetries + " retries.");
	}
}

