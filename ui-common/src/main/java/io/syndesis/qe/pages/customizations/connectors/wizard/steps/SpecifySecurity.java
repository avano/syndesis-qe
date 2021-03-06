package io.syndesis.qe.pages.customizations.connectors.wizard.steps;

import static org.assertj.core.api.Assertions.assertThat;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

import io.syndesis.qe.TestConfiguration;
import io.syndesis.qe.logic.common.wizard.WizardPhase;
import io.syndesis.qe.pages.SyndesisPageObject;
import io.syndesis.qe.utils.ByUtils;

import org.openqa.selenium.By;

import com.codeborne.selenide.SelenideElement;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpecifySecurity extends SyndesisPageObject implements WizardPhase {

    private static class Button {
        public static By NEXT = By.xpath(".//button[contains(.,'Next')]");
    }

    public static class Element {
        public static By SECURITY_FORM = ByUtils.dataTestId("api-client-connector-auth-type-form");
    }

    private static class Input {
        public static By AUTHORIZATION_URL = By.xpath(".//input[@formcontrolname='authorizationEndpoint']");
        public static By ACCESS_TOKEN_URL = By.xpath(".//input[@formcontrolname='tokenEndpoint']");
    }

    public static class SecurityType {
        public static By HTTP_BASIC_AUTHENTICATION =
            ByUtils.containsDataTestId("api-client-connector-auth-type-basic-");
        public static By NO_SECURITY =
            ByUtils.containsDataTestId("api-client-connector-auth-type-none");
        public static By API_KEY =
            ByUtils.containsDataTestId("api-client-connector-auth-type-apikey");
        public static By OAUTH_2 =
            ByUtils.containsDataTestId("api-client-connector-auth-type-oauth2");
    }

    @Override
    public void goToNextWizardPhase() {
        $(Button.NEXT).shouldBe(visible).click();
    }

    @Override
    public SelenideElement getRootElement() {
        return $(Element.SECURITY_FORM).should(exist);
    }

    @Override
    public boolean validate() {
        return getRootElement().exists();
    }

    public void setUpSecurityProperties(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            switch (key) {
                case "authorizationUrl":
                    log.info("Setting up authorization url property");
                    getRootElement().$(Input.AUTHORIZATION_URL).setValue(properties.get(key));
                    break;
                case "accessTokenUrl":
                    log.info("Setting up access token url property");

                    if (properties.get(key).equals("syndesisUrl+syndesisCallbackUrlSuffix")) {
                        getRootElement().$(Input.ACCESS_TOKEN_URL).setValue(TestConfiguration.syndesisUrl() + TestConfiguration.syndesisCallbackUrlSuffix());
                    } else {
                        getRootElement().$(Input.ACCESS_TOKEN_URL).setValue(properties.get(key));
                    }
                    break;
                default:
            }
        }
    }

    public void selectOauth2() {
        List<SelenideElement> listWithOAuthElement = $$(By.id("authenticationType")).stream()
            .filter(e -> "OAuth 2.0".equals(e.parent().text()))
            .collect(Collectors.toList());
        assertThat(listWithOAuthElement).hasSize(1);
        listWithOAuthElement.get(0).click();
    }

    public void selectHttpBasicAuthentication() {
        if (getRootElement().$(SecurityType.HTTP_BASIC_AUTHENTICATION).attr("readonly") == null) {
            getRootElement().$(SecurityType.HTTP_BASIC_AUTHENTICATION).setSelected(true);
        }
    }
}
