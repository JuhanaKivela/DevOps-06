package com.example.springboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SpringbootApplication {
	// To run this:
	// mvn spring-boot:run
	public static void main(String[] args) {
		SpringApplication.run(SpringbootApplication.class, args);
	}

	// Gather system information from both systems
	// Gathers IP address, top 10 processes, remaining disk space, and uptime
	private static String gatherBothSystemInformation() {
    	try {
    	    String ip_address = InetAddress.getLocalHost().getHostAddress();

    	    // System1 information
    	    String processInfo = runCLICommand("bash", "-c", "ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%cpu | head -n 10");
    	    String remainingDiskSpace = runCLICommand("bash", "-c", "df -h --output=avail / | tail -n 1");
    	    String uptime = runCLICommand("bash", "-c", "uptime -p");

			String system1InfoString = "\nService1\n    -IP: " + ip_address + "\n\n    -Top 10 Processes: \n" + processInfo + "\n    -Remaining Disk Space: " + remainingDiskSpace + "\n    -Uptime: " + uptime;

			// System2 information

    	    return system1InfoString;
    	} catch (Exception e) {
    	    return "Error when getting system information: " + e.getMessage();
    	}
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
	@RestController
	static class SysInfoController {
		@RequestMapping("/")
		public String getSysInfo() {
			return gatherBothSystemInformation();
		}
	}
}
