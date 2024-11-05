package com.example.springboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

@SpringBootApplication
public class SpringbootApplication {

	private boolean serviceSleeping = false;

	// To run this:
	// mvn spring-boot:run
	public static void main(String[] args) {
		SpringApplication.run(SpringbootApplication.class, args);
	}

	// Gather system information from both systems
	// Gathers IP address, processes, remaining disk space, and uptime
	private static String gatherBothSystemInformation() {
    	try {
    	    // System1 information
    	    String ip_address = InetAddress.getLocalHost().getHostAddress();
    	    String processInfo = runCLICommand("bash", "-c", "top -b -n 1 | sed '1,6d'");
    	    String remainingDiskSpace = runCLICommand("bash", "-c", "df -h --output=avail / | tail -n 1");
    	    String uptime = runCLICommand("bash", "-c", "uptime -p");

			String system1InfoString = formatSystemInfo("Service1", ip_address, processInfo, remainingDiskSpace, uptime);

			// System2 information
			RestTemplate restTemplate = new RestTemplate();
			Map<String, Object> system2Info = restTemplate.getForObject("http://service2:3001/sysInfo", Map.class);

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
	private static String runCLICommand(String... command) throws Exception {
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
	}

	// Controller to handle requests to the root of the server
	@Controller
	static class SysInfoController {
		private final SpringbootApplication application;

		public SysInfoController(SpringbootApplication application) {
			this.application = application;
		}

		/*@RequestMapping("/")
		public String getSysInfo() {
			System.out.println("Received request for system information");
			if(application.serviceSleeping){
			}
			ModelAndView modelAndView = new ModelAndView();
			modelAndView.setViewName("mainPage.html");
			return modelAndView;
			return 
		}*/
	}
}