/**
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.xmlsender.resources;

import io.github.project.openubl.xmlsender.models.DeliveryStatusType;
import io.github.project.openubl.xmlsender.models.jpa.DocumentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.restassured.response.Response;
import org.awaitility.Duration;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DocumentsResourceTest {

    @Test
    void withNoFileShouldReturnError() {
        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("myParamName", "myParamValue")
                    .post("/documents")
                .then()
                    .statusCode(400)
                    .body("error", is("Form[file] is required"));
    }

    @Test
    void withEmptyFileShouldReturnError() {
        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", "")
                    .post("/documents")
                .then()
                    .statusCode(400)
                    .body("error", is("Form[file] is empty"));
    }

    @Test
    void withInvalidFileShouldReturnError() {
        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", "anyContent")
                    .post("/documents")
                .then()
                    .statusCode(400)
                    .body("error", is("Form[file] is not a valid XML file or is corrupted"));
    }

    @Test
    void invoice_withSystemCredentials_shouldReturnOK() {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("xmls/invoice.xml");
        assertNotNull(resource);
        File file = new File(resource.getPath());

        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", file, "application/xml")
                        .formParam("customId", "myCustomSoftwareID")
                    .post("/documents")
                .then()
                    .statusCode(200)
                    .body("id", notNullValue())
                    .body("fileID", notNullValue())
                    .body("deliveryStatus", is("SCHEDULED_TO_DELIVER"))
                    .body("customId", is("myCustomSoftwareID"))
                    .body("fileInfo.ruc", is("12345678912"))
                    .body("fileInfo.filename", is("12345678912-01-F001-1"))
                    .body("fileInfo.documentID", is("F001-1"))
                    .body("fileInfo.documentType", is("Invoice"))
                    .body("fileInfo.deliveryURL", is("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService"))
                    .body("sunatCredentials.username", nullValue())
                    .body("sunatCredentials.password", nullValue());
    }

    @Test
    void invoice_withCustomCredentials_shouldReturnOK() {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("xmls/invoice.xml");
        assertNotNull(resource);
        File file = new File(resource.getPath());

        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", file, "application/xml")
                        .formParam("customId", "myCustomSoftwareID")
                        .formParam("username", "myUsername")
                        .formParam("password", "myPassword")
                    .post("/documents")
                .then()
                    .statusCode(200)
                    .body("id", notNullValue())
                    .body("fileID", notNullValue())
                    .body("deliveryStatus", is("SCHEDULED_TO_DELIVER"))
                    .body("customId", is("myCustomSoftwareID"))
                    .body("fileInfo.ruc", is("12345678912"))
                    .body("fileInfo.filename", is("12345678912-01-F001-1"))
                    .body("fileInfo.documentID", is("F001-1"))
                    .body("fileInfo.documentType", is("Invoice"))
                    .body("fileInfo.deliveryURL", is("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService"))
                    .body("sunatCredentials.username", is("myUsername"))
                    .body("sunatCredentials.password", is("******"));
    }

    @Test
    void invoice_downloadFile() throws IOException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("xmls/invoice.xml");
        assertNotNull(resource);
        File file = new File(resource.getPath());

        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", file, "application/xml")
                        .formParam("customId", "myCustomSoftwareID")
                    .post("/documents")
                .then()
                    .statusCode(200)
                    .body("id", is(1));

        Response response = given()
                .when().get("/documents/1/file")
                .thenReturn();

        assertEquals(200, response.getStatusCode());

        byte[] bytes = response.getBody().asByteArray();
        assertArrayEquals(Files.readAllBytes(Paths.get(resource.getPath())), bytes);
    }

    @Test
    void invalidInvoice_systemCredentials_shouldBeSentToSunat() throws InterruptedException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("xmls/invoice_signed.xml");
        assertNotNull(resource);
        File file = new File(resource.getPath());

        given()
                .when()
                    .header(new Header("content-type", "multipart/form-data"))
                    .multiPart("file", file, "application/xml")
                        .formParam("customId", "myCustomSoftwareID")
                        .formParam("username", "12345678912MODDATOS")
                        .formParam("password", "MODDATOS")
                    .post("/documents")
                .then()
                    .statusCode(200)
                    .body("id", is(1))
                    .body("cdrID", nullValue())
                    .body("fileID", notNullValue())
                    .body("deliveryStatus", is("SCHEDULED_TO_DELIVER"));

//        await()
//                .atMost(5000, TimeUnit.MILLISECONDS)
//                .with().pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
//                .until(() -> {
//                    given()
//                            .when().get("/documents/1")
//                            .then()
//                            .statusCode(200)
//                            .body("deliveryStatus", is("DELIVERED"));
//                });

        given()
                .when().get("/documents/1")
                .then()
                    .statusCode(200)
                    .body("id", is(1))
                    .body("cdrID", notNullValue())
                    .body("fileID", notNullValue())
                    .body("deliveryStatus", is("DELIVERED"))
                    .body("sunatStatus.code", is(0))
                    .body("sunatStatus.ticket", nullValue())
                    .body("sunatStatus.status", is("ACEPTADO"))
                    .body("sunatStatus.description", is("La Factura numero F001-1, ha sido aceptada"));

        Response response = given()
                .when().get("/documents/1/cdr")
                .thenReturn();
        assertEquals(200, response.getStatusCode());
    }

}