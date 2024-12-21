import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class ApiTests {

    @BeforeAll
    public static void setUp() {
        // URL for the testing API in port 8197
        RestAssured.baseURI = "http://localhost:8197";
    }

    @Test
    public void testStateEndpoint() {
        given()
            .when()
            .get("/state")
            .then()
            .statusCode(200)
            .body(equalTo("INIT"));
    }
}
