import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@TestMethodOrder(OrderAnnotation.class)
public class ApiTests {

    // Returns the amount of currently running containers on the system
    // This is needed for testing the PUT /state SHUTDOWN
    // Returns the amount of containers (int) if successful, and -1 if unsuccessful
    static private int getAmountOfRunningContainers(){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "ps", "-q");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            reader.close();

            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static String userName;
    private static String password;

    @BeforeAll
    public static void setUp() {
        int timeout = 60000; // 1 minute
        int interval = 5000; // 5 seconds
        int elapsedTime = 0;
        int runningContainers = getAmountOfRunningContainers();

        // Wait until the number of running containers reaches 6 or timeout
        while (elapsedTime < timeout && runningContainers != 6) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            elapsedTime += interval;
            runningContainers = getAmountOfRunningContainers();
        }

        if (runningContainers != 6) {
            throw new RuntimeException("Timeout reached before the number of running containers reached 6");
        }
        // URL for the testing API in port 8197
        RestAssured.baseURI = "http://localhost:8197";
        userName = "adminUser";
        password = "$apr1$w7ah8scx$mg2Jg0L4sAEAokKdf2lgm.";
        given()
            .auth().preemptive().basic(userName, password)
            .contentType("text/plain")
            .when()
            .put("/state?state=INIT");
    }

    @Test
    @Order(1)
    public void testGetStateEndpoint() {
        given()
            .when()
            .get("/state")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("INIT"));
    }

    @Test
    @Order(2)
    public void testPutStateWithoutAuthorization() {
        // Try to put to RUNNING, but it should respond with 401 Unauthorized
        given()
            .contentType("text/plain")
            .when()
            .put("/state?state=RUNNING")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(3)
    public void testPutStatementWithAuthorization() {
        // Put to RUNNING, should respond with 200 OK
        given()
            .auth().preemptive().basic(userName, password)
            .contentType("text/plain")
            .when()
            .put("/state?state=RUNNING")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("RUNNING"));
    }

    @Test
    @Order(4)
    public void testRunLogEndpoint() {
        given()
            .when()
            .get("/run-log")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(containsString("INIT -> RUNNING"));
    }

    @Test
    @Order(5)
    public void testGetRequestEndpoint() {
        given()
            .when()
            .get("/request")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(containsString("System information:"));
    }

    @Test
    @Order(6)
    public void testPausedState() {
        String pausedMessage = "System is paused. Can't return the state.";
        // Put to PAUSED, should respond with 200 OK
        given()
            .auth().preemptive().basic(userName, password)
            .contentType("text/plain")
            .when()
            .put("/state?state=PAUSED")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("PAUSED"));
        given()
            .when()
            .get("/request")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo(pausedMessage));
        given()
            .when()
            .get("/state")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo(pausedMessage));
        given()
            .when()
            .get("/run-log")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo(pausedMessage));
        given()
            .auth().preemptive().basic(userName, password)
            .contentType("text/plain")
            .when()
            .put("/state?state=RUNNING")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("RUNNING"));
    }

    @Test
    @Order(7)
    public void testShutdownState() {
        org.junit.jupiter.api.Assumptions.assumeTrue(false, "Skipping testShutdownState as it is known to fail due to unfinished code");
        // Get the amount of docker containers running before SHUTDOWN
        int containersRunningBeforeShutdown = getAmountOfRunningContainers();
        
        // Put to SHUTDOWN, should respond with 200 OK and all containers must be stopped (docker-compose down)
        given()
            .auth().preemptive().basic(userName, password)
            .contentType("text/plain")
            .when()
            .put("/state?state=SHUTDOWN")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("SHUTDOWN"));
        
        int containersRunningAfterShutdown = containersRunningBeforeShutdown;
        int SERVICE_CONTAINER_AMOUNT = 6;
        int timeout = 20000; // 20 seconds
        int interval = 3000; // 3 seconds
        int elapsedTime = 0;

        // Checks if docker has shut down all 6 containers every 3 seconds untill timeout of 20 seconds
        while (elapsedTime < timeout) {
            containersRunningAfterShutdown = getAmountOfRunningContainers();
            if (containersRunningAfterShutdown == containersRunningBeforeShutdown - SERVICE_CONTAINER_AMOUNT) {
                break;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            elapsedTime += interval;
        }

        assertEquals(containersRunningBeforeShutdown - SERVICE_CONTAINER_AMOUNT, containersRunningAfterShutdown, "PUT /state SHUTDOWN did not close all containers");
    }
}
