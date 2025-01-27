package com.example.springboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class SpringbootApplication {

	private boolean serviceSleeping = false;
	private static RestTemplate restTemplate = null;

	// Creates singleton of redisTemplate
	@Autowired
    private RedisTemplate<String, Object> redisTemplate;

	// Keys for accessing values from Redis
	private static final String STATE_KEY = "serviceState";
    private static final String LOGS_KEY = "serviceLogs";
		
	// To run this:
	// mvn spring-boot:run
	public static void main(String[] args) {
		SpringApplication.run(SpringbootApplication.class, args);
		restTemplate = new RestTemplateBuilder()
		.setConnectTimeout(Duration.ofSeconds(2))
		.setReadTimeout(Duration.ofSeconds(2))
		.build();
	}

	@PostConstruct
    public void init() {

        redisTemplate.opsForValue().set(STATE_KEY, "INIT");
		redisTemplate.opsForList().trim(LOGS_KEY, 1, 0);
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

    private void shutdownDockerCompose() {
        try {
            // Stop all containers
            ProcessBuilder stopProcessBuilder = new ProcessBuilder("docker", "ps", "-q");
            Process stopProcess = stopProcessBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stopProcess.getInputStream()));
            String containerId;
            while ((containerId = reader.readLine()) != null) {
                new ProcessBuilder("docker", "stop", containerId).start().waitFor();
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private void log(String newStatus) {
		String currentTime = java.time.LocalTime.now().toString();
		String stringToAdd = currentTime + ": " + getSystemState() + " -> " + newStatus;
		redisTemplate.opsForList().rightPush(LOGS_KEY, stringToAdd);

	}

	// Gets value for system state in Redis
	private String getSystemState() {
		return (String) redisTemplate.opsForValue().get(STATE_KEY);
	}

	// Stores new system state to Redis
	private void setSystemState(String state) {
        redisTemplate.opsForValue().set(STATE_KEY, state);
    }

	// Controller to handle requests to the root of the server
	@Controller
	class SysInfoController {

		@RequestMapping("/request")
		@ResponseBody
		public String getSysInfo() {
			if(getSystemState().equals("PAUSED")){
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
			if(getSystemState().equals("PAUSED")){
				return "System is paused. Can't return the state.";
			}
			return getSystemState();
		}

		@PutMapping("/state")
		@ResponseBody
		public ResponseEntity<String> setState(String state) {
		    HttpHeaders headers = new HttpHeaders();
		    String currentState = getSystemState();
		
		    switch (state) {
		        case "PAUSED":
		            if (currentState.equals("PAUSED")) {
		                return new ResponseEntity<>("PAUSED", headers, HttpStatus.OK);
		            }
		            if (!currentState.equals("RUNNING")) {
		                return new ResponseEntity<>("System needs to be in RUNNING state in order to be put to PAUSED.", headers, HttpStatus.BAD_REQUEST);
		            }
		            break;
				
		        case "RUNNING":
		            if (currentState.equals("RUNNING")) {
		                return new ResponseEntity<>("RUNNING", headers, HttpStatus.OK);
		            }
		            break;
				
		        case "INIT":
		            serviceSleeping = false;
		            headers.add("WWW-Authenticate", "Basic realm=\"Restricted\"");
		            break;
				
		        case "SHUTDOWN":
		            if (currentState.equals("INIT")) {
		                return new ResponseEntity<>("System is in INIT state. Can't SHUTDOWN from here.", headers, HttpStatus.BAD_REQUEST);
		            }
		            shutdownDockerCompose();
		            break;
				
		        default:
		            return new ResponseEntity<>("INVALID", headers, HttpStatus.BAD_REQUEST);
		    }
		
		    log(state);
		    setSystemState(state);
		    return new ResponseEntity<>(state, headers, HttpStatus.OK);
		}

        @GetMapping("/run-log")
        @ResponseBody
        public String getRunLog() {
            if(getSystemState().equals("PAUSED")){
                return "System is paused. Can't return the state.";
            }

            List<Object> logs = redisTemplate.opsForList().range(LOGS_KEY, 0, -1);
            if(logs == null || logs.isEmpty()) {
                return "[]";
            }

            return String.join("\n", logs.stream().map(Object::toString).toArray(String[]::new));
        }
	}
}