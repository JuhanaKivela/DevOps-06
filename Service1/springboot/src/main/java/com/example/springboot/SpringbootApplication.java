package com.example.springboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class SpringbootApplication {

	private boolean serviceSleeping = false;
	private static RestTemplate restTemplate = null;

	private enum ServiceState {
		INIT, PAUSED, RUNNING, SHUTDOWN
	}

	private ServiceState currentState = ServiceState.INIT;

	private List<String> logs = new ArrayList<String>();
		
	// To run this:
	// mvn spring-boot:run
	public static void main(String[] args) {
		SpringApplication.run(SpringbootApplication.class, args);
		restTemplate = new RestTemplateBuilder()
		.setConnectTimeout(Duration.ofSeconds(2))
		.setReadTimeout(Duration.ofSeconds(2))
		.build();
	}
	
	// Gather system information from both systems
	// Gathers IP address, processes, remaining disk space, and uptime
	private String gatherBothSystemInformation() {
		try {
			// System1 information
			String ip_address = InetAddress.getLocalHost().getHostAddress();
			String processInfo = runCLICommand("bash", "-c", "top -b -n 1 | sed '1,6d'");
			String remainingDiskSpace = runCLICommand("bash", "-c", "df -h --output=avail / | tail -n 1");
			String uptime = runCLICommand("bash", "-c", "uptime -p");

			String system1InfoString = formatSystemInfo("Service1", ip_address, processInfo, remainingDiskSpace, uptime);

			// System2 information
			Map<String, Object> system2Info;
			try {
				Map<String, Object> tempSystem2Info = restTemplate.getForObject("http://service2:3001/sysInfo", Map.class);
			system2Info = tempSystem2Info;
			} catch (Exception e) {
				system2Info = Map.of(
					"ip", "N/A",
					"topProcesses", "N/A",
					"diskSpace", "N/A",
					"uptime", "N/A"
				);
			}

		String system2InfoString = formatSystemInfo("Service2", (String) system2Info.get("ip"), (String) system2Info.get("topProcesses"), (String) system2Info.get("diskSpace"), (String) system2Info.get("uptime"));

		return system1InfoString + "\n" + system2InfoString;
		} catch (Exception e) {
			return "Error when getting system information: " + e.getMessage();
		}
	}

	private static String formatSystemInfo(String serviceName, String ip_address, String processes, String remainingDiskSpace, String uptime){
		return serviceName + "\n      - IP: " + ip_address + "\n\n      - Processes: \n" + processes + "\n      - Remaining Disk Space: " + remainingDiskSpace + "\n      - Uptime: " + uptime;
	}

	// Run a command in the CLI of the host machine
	private String runCLICommand(String... command) throws Exception {
    	ProcessBuilder processBuilder = new ProcessBuilder(command);
    	Process process = processBuilder.start();
		
    	try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
    	    StringBuilder output = new StringBuilder();
    	    String line;
    	    while ((line = reader.readLine()) != null) {
    	        output.append(line).append("\n");
    	    }
    	    process.waitFor();
    	    return output.toString();
    	}
		catch (Exception e) {
			process.destroy();
			return "N/A";
		}
	}

	private void log(String newStatus) {
		String currentTime = java.time.LocalTime.now().toString();
		String stringToAdd = currentTime + ": " + currentState.toString() + " -> " + newStatus;
		logs.add(stringToAdd);

	}

	// Controller to handle requests to the root of the server
	@Controller
	class SysInfoController {

		@RequestMapping("/request")
		@ResponseBody
		public String getSysInfo() {
			if(currentState != ServiceState.RUNNING){
				return "System is paused. Can't return the state.";
			}

			String containerName = System.getenv("CONTAINER_NAME");
			if(serviceSleeping) {
				return containerName + " is still sleeping. Try again later.";
			}
			String response = "Fetched from instance: " + containerName + "\n\nSystem information:\n" + gatherBothSystemInformation();

			new Thread(() -> {
                try {
					serviceSleeping = true;
                    Thread.sleep(2000); // Sleep for 2 seconds
					serviceSleeping = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
			return response;
		}

		@GetMapping("/state")
		@ResponseBody
		public String getState() {
			if(currentState == ServiceState.PAUSED){
				return "System is paused. Can't return the state.";
			}
			return currentState.toString();
		}

		@PutMapping("/state")
		@ResponseBody
		public ResponseEntity<String> setState(String state) {
			HttpHeaders headers = new HttpHeaders();
			if(state.equals("PAUSED")) {
				// Don't do anything if the service is already paused
				if(currentState == ServiceState.PAUSED) {
					return new ResponseEntity<>("PAUSED", headers, HttpStatus.OK);
				}
				log(state);
				currentState = ServiceState.PAUSED;
				return new ResponseEntity<>("PAUSED", headers, HttpStatus.OK);
			}
			else if(state.equals("RUNNING")) {
				// Don't do anything if the service is already running
				if(currentState == ServiceState.RUNNING) {
					return new ResponseEntity<>("RUNNING", headers, HttpStatus.OK);
				}
				log(state);
				currentState = ServiceState.RUNNING;
				return new ResponseEntity<>("RUNNING", headers, HttpStatus.OK);
			}
			else if (state.equals("INIT")) {
				log(state);
				currentState = ServiceState.INIT;
				serviceSleeping = false;
				headers.add("WWW-Authenticate", "Basic realm=\"Restricted\"");
				return new ResponseEntity<>("INIT", headers, HttpStatus.OK);
			}
			else if(state.equals("SHUTDOWN")) {
				log(state);
				currentState = ServiceState.SHUTDOWN;
				// TODO: Shutdown all the docker containers
				return new ResponseEntity<>("SHUTDOWN", headers, HttpStatus.OK);
			}
			else {
				return new ResponseEntity<>("INVALID", headers, HttpStatus.BAD_REQUEST);
			}
		}

		@GetMapping("/run-log")
		@ResponseBody
		public String getRunLog() {
			if(currentState == ServiceState.PAUSED){
				return "System is paused. Can't return the state.";
			}

			if(logs == null || logs.isEmpty()) {
				return "[]";
			}

			return String.join("\n", logs);
		}
	}
}