package com.example.springboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class SpringbootApplication {

	private boolean serviceSleeping = false;
	private static RestTemplate restTemplate = null;
		
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

	// Controller to handle requests to the root of the server
	@Controller
	class SysInfoController {

		@RequestMapping("/request")
		@ResponseBody
		public String getSysInfo() {
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
	}
}