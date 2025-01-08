import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;


public class ApiTests {

    private static String userName;
    private static String password;

    @BeforeAll
    public static void setUp() {
        // URL for the testing API in port 8197
        RestAssured.baseURI = "http://localhost:8197";
        userName = "adminUser";
        password = "$apr1$w7ah8scx$mg2Jg0L4sAEAokKdf2lgm.";
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
            .body(equalTo("RUNNING"));
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
}
