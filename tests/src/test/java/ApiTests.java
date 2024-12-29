import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import java.beans.Transient;
import java.lang.annotation.Target;

public class ApiTests {

    @BeforeAll
    public static void setUp() {
        // URL for the testing API in port 8197
        RestAssured.baseURI = "http://localhost:8197";
    }

    @Test
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
    public void testPutStateEndpoint() {
        given()
            .contentType("text/plain")
            .body("INIT")
            .when()
            .put("/state")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("INIT"));
    }

    @Test
    public void testPutStateWithAuthorization() {
        // Set to INIT, which should forget authorization
        given()
            .contentType("text/plain")
            .body("INIT")
            .when()
            .put("/state")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("INIT"));

        // Try to put to RUNNING, but it should respond with 401 Unauthorized
        given()
            .contentType("text/plain")
            .body("RUNNING")
            .when()
            .put("/state")
            .then()
            .statusCode(401);
    }

    @Test
    public void testRunLogEndpoint() {
        given()
            .when()
            .get("/run-log")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(equalTo("[]"));
    }
}
