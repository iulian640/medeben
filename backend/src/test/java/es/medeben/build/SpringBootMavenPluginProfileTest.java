package es.medeben.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce el issue #218: el arranque documentado ("cd backend && mvn
 * spring-boot:run") moría con "Secreto JWT de desarrollo detectado fuera de
 * dev/local/test". Causa: JwtConfig exige un perfil ACTIVO dev/local/test
 * (barrera fail-fast, hallazgo H1 del security-review) y
 * spring.profiles.default=dev de application.yml NO cuenta como activo — a
 * propósito, para que un despliegue sin SPRING_PROFILES_ACTIVE no firme
 * tokens con el secreto público del repo. spring-boot:run tampoco activa
 * ningún perfil por sí solo, así que el arranque documentado caía en esa
 * barrera. La barrera (JwtConfig) NO se toca: el arreglo activa el perfil
 * dev explícitamente solo para el goal "run" (desarrollo local) vía
 * configuración del plugin.
 */
@DisplayName("pom.xml — spring-boot-maven-plugin activa el perfil dev en 'run'")
class SpringBootMavenPluginProfileTest {

    @Test
    @DisplayName("el goal run configura el perfil dev (arranque documentado sin SPRING_PROFILES_ACTIVE)")
    void goalRunActivaPerfilDev() throws Exception {
        File pom = new File("pom.xml");
        assertThat(pom).as("este test asume cwd=backend/ (así corre 'mvn test')").exists();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(pom);

        XPath xpath = XPathFactory.newInstance().newXPath();
        String expresion =
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                        + "/configuration/profiles/profile[text()='dev']";
        NodeList nodos = (NodeList) xpath.evaluate(expresion, doc, XPathConstants.NODESET);

        assertThat(nodos.getLength())
                .as("spring-boot-maven-plugin debe declarar <profiles><profile>dev</profile></profiles> "
                        + "en su <configuration> para que 'mvn spring-boot:run' arranque con la "
                        + "barrera fail-fast de JwtConfig satisfecha")
                .isEqualTo(1);
    }
}
