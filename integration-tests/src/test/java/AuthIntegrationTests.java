import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegrationTests {
    @BeforeAll
    static void setUp() {
        // Tests run as if accessing services via the internet. i.e. tests don't run inside the docker context.
        // All services running inside the docker network and access only through the api-gateway.
        RestAssured.baseURI = "http://localhost:4004/";
    }

    @Test
    public void shouldReturnOKWithValidToken() {
        // 1. Arrange
        // 2. Act
        // 3. Assert

        // 1. Arrange
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password": "password123"
                }
                """;

        Response response =
                //1. Arrange
                given()
                        .contentType("application/json")
                        .body(loginPayload)
                        // 2. Act
                        .when()
                        .post("/auth/login")
                        // 3. Assert
                        .then()
                        .statusCode(200)
                        .body("token", notNullValue())
                        .extract()
                        .response();

        System.out.println("Generated token: " + response.jsonPath().getString("token"));
    }

    @Test
    public void shouldReturnBadRequestWithInvalidToken() {
        String loginPayload = """
                {
                    "email": "invaliduser@test.com",
                    "password": "wrongpassword"
                }
                """;

        given()
                .contentType("application/json")
                .body(loginPayload)
                // 2. Act
                .when()
                .post("/auth/login")
                // 3. Assert
                .then()
                .statusCode(401);
    }

}
