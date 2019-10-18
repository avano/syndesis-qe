package io.syndesis.qe.upgrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.syndesis.qe.TestConfiguration;
import io.syndesis.qe.endpoints.IntegrationsEndpoint;
import io.syndesis.qe.utils.OpenShiftUtils;
import io.syndesis.qe.utils.RestUtils;
import io.syndesis.qe.utils.TestUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.openshift.api.model.DeploymentConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@Slf4j
public class UpgradeSteps {
    private static final String SYNDESIS = Paths.get("..", "..", "syndesis").toAbsolutePath().toString();
    private static final String UPGRADE_FOLDER = Paths.get(SYNDESIS, "tools", "upgrade").toFile().toString();
    private static final String UPGRADE_TEMPLATE = Paths.get(SYNDESIS, "install", "syndesis.yml").toString();
    private static final String VERSION_ENDPOINT = "/api/v1/version";
    private static final String DOCKER_HUB_SYNDESIS_TAGS_URL = "https://hub.docker.com/v2/repositories/syndesis/syndesis-server/tags/?page_size=1024";
    private static final String BACKUP_DIR = "/tmp/backup";

    @Autowired
    private IntegrationsEndpoint integrationsEndpoint;

    private String integrationId;

    @When("^get upgrade versions$")
    public void getUpgradeVersions() {
        if (System.getProperty("syndesis.upgrade.version") == null) {
            // Parse "1.5"
            final BigDecimal version = new BigDecimal(Double.parseDouble(StringUtils.substring(System.getProperty("syndesis.version"), 0, 3)))
                .setScale(1, BigDecimal.ROUND_HALF_UP);
            Request request = new Request.Builder()
                .url(DOCKER_HUB_SYNDESIS_TAGS_URL)
                .build();
            String response = "";
            try {
                response = new OkHttpClient.Builder().build().newCall(request).execute().body().string();
            } catch (IOException e) {
                log.error("Unable to get version from " + VERSION_ENDPOINT);
                e.printStackTrace();
            }

            JSONArray jsonArray = new JSONObject(response).getJSONArray("results");
            List<String> tags = new ArrayList<>();
            for (Object o : jsonArray) {
                tags.add(((JSONObject) o).getString("name"));
            }

            // Grab "1.7.X" if exists, otherwise get the daily
            Optional<String> tag = tags.stream().filter(
                t -> t.matches("^" + version.toString().replaceAll("\\.", "\\\\.") + "\\.\\d+")
            ).findFirst();
            if (!tag.isPresent()) {
                List<String> daily = tags.stream().filter(
                    t -> t.matches("^" + (version + "").replaceAll("\\.", "\\\\.") + "(\\.\\d+)?-\\d{8}$")
                ).collect(Collectors.toList());
                tag = Optional.of(daily.get(daily.size() - 1));
            }
            log.info("Setting syndesis.upgrade.version to " + tag.get());
            System.setProperty("syndesis.upgrade.version", tag.get() + "");

            // Get penultimate version - not daily
            BigDecimal previousVersion = version;
            while (previousVersion.doubleValue() >= 1.0) {
                previousVersion = previousVersion.subtract(new BigDecimal(0.1));
                BigDecimal finalPreviousVersion = previousVersion;
                Optional<String> previousTag = tags.stream().filter(
                    t -> t.matches("^" + (finalPreviousVersion.doubleValue() + "").replaceAll("\\.", "\\\\.") + "(\\.\\d+)?$")
                ).findFirst();
                if (previousTag.isPresent()) {
                    log.info("Setting syndesis.version to " + previousTag.get());
                    // Save the original syndesis version
                    System.setProperty("syndesis.upgrade.backup.version", System.getProperty("syndesis.version"));
                    System.setProperty("syndesis.version", previousTag.get());
                    break;
                }
            }
        }

        if (System.getProperty("syndesis.upgrade.old.version") != null) {
            // Allow to define daily tag using custom property, because you can't define daily version as "syndesis.version"
            // because there are no artifacts
            System.getProperty("syndesis.upgrade.backup.version", System.getProperty("syndesis.version"));
            System.setProperty("syndesis.version", System.getProperty("syndesis.upgrade.old.version"));
        }

        //        TestConfiguration.get().overrideSyndesisVersion(System.getProperty("syndesis.version"));

        log.info("Upgrade:");
        log.info("Old version: " + System.getProperty("syndesis.version"));
        log.info("New version: " + System.getProperty("syndesis.upgrade.version"));
    }

    @When("^perform syndesis upgrade to newer version$")
    public void syndesisUpgrade() {
        ProcessBuilder pb = new ProcessBuilder(Paths.get(UPGRADE_FOLDER, "upgrade.sh").toString(),
            "--template ", UPGRADE_TEMPLATE,
            "--backup", BACKUP_DIR,
            "--oc-login",
            "oc login " + TestConfiguration.openShiftUrl() + " --token=" + OpenShiftUtils.getInstance().getConfiguration().getOauthToken() + " -n " +
                TestConfiguration.openShiftNamespace(),
            "--migration", Paths.get(UPGRADE_FOLDER, "migration").toString());
        pb.directory(new File(UPGRADE_FOLDER));

        try {
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
            p.waitFor();
        } catch (Exception e) {
            log.error("Error while running upgrade script: ", e);
            e.printStackTrace();
        }
    }

    @When("^perform syndesis upgrade to newer version using operator$")
    public void upgradeUsingOperator() {
        // TODO: @avano needs to fix this :D
        //        try (InputStream is = new URL(TestConfiguration.syndesisOperatorUrl().replace(System.getProperty("syndesis.version"), System
        //        .getProperty(
        //            "syndesis.upgrade.version"))).openStream()) {
        //            List<HasMetadata> resources = OpenShiftUtils.getInstance().load(is).get();
        //            for (HasMetadata resource : resources) {
        //                if (resource instanceof DeploymentConfig) {
        //                    //OCP4HACK - "the API version in the data (v1) does not match the expected API version (apps.openshift.io/v1)"
        //                    DeploymentConfig dc = (DeploymentConfig) resource;
        //                    dc.setApiVersion("apps.openshift.io/v1");
        //                    OpenShiftUtils.getInstance().deploymentConfigs().createOrReplace(dc);
        //                } else if (resource instanceof ImageStream) {
        //                    OpenShiftUtils.getInstance().imageStreams().createOrReplace((ImageStream) resource);
        //                }
        //            }
        //        } catch (Exception e) {
        //            fail("Unable to deploy " + System.getProperty("syndesis.upgrade.version") + " operator: ", e);
        //        }
    }

    @Then("^verify syndesis version$")
    public void verifyVersion() {
        assertThat(getSyndesisVersion()).isEqualTo(System.getProperty("syndesis.version"));
    }

    @When("^perform test modifications$")
    public void performTestModifications() {
        modifyTemplate();
        modifyDbScripts();
        modifyUpgradeDbScript();
        copyStatefulScripts();
        getSyndesisCli();
    }

    private void modifyTemplate() {
        // Change the install template to use newer version
        runShellInSyndesisHome("git", "fetch", "--tag");
        // Checkout the tag so that we don't need to modify the versions in the template
        runShellInSyndesisHome("git", "checkout", System.getProperty("syndesis.upgrade.version"));
        String template;
        try {
            template = FileUtils.readFileToString(new File(UPGRADE_TEMPLATE), "UTF-8");
            // Modify deployment config
            // This is easier than messing with yaml directly and it adds the env for syndesis-meta and syndesis-server
            if (!template.contains("- name: TEST")) {
                template = StringUtils.replaceAll(template, "tmp\"", "tmp\"\n          - name: TEST\n            value: \"UPGRADE\"");
            }
            FileUtils.write(new File(UPGRADE_TEMPLATE), template, "UTF-8", false);
        } catch (IOException e) {
            log.error("Unable to modify template", e);
        }
    }

    private void modifyDbScripts() {
        integrationId = integrationsEndpoint.getIntegrationId("upgrade").get();
        String upgradeResourcesPath = new File("src/test/resources/upgrade").getAbsolutePath();
        // Replace placeholder in upgrade scripts
        createFileFromTemplate(upgradeResourcesPath, "up-98-template.js", "INTEGRATION_ID", integrationId);
        createFileFromTemplate(upgradeResourcesPath, "up-99-template.js", "INTEGRATION_ID", integrationId);
    }

    private void modifyUpgradeDbScript() {
        String upgradeResourcesPath = new File("src/test/resources/upgrade").toURI().toString();
        // Make the syndesis-cli migrate to newest version and use scripts from resources
        String upgradeDb;
        try {
            upgradeDb = FileUtils.readFileToString(Paths.get(UPGRADE_FOLDER, "steps", "upgrade_10_migrate_db").toFile(), "UTF-8");
            if (!upgradeDb.contains("-t 99")) {
                upgradeDb = upgradeDb.replaceAll("syndesis-cli.jar migrate", "syndesis-cli.jar migrate -t 99 -f "
                    + upgradeResourcesPath);

                upgradeDb = upgradeDb.replaceAll("port=5432", "port=5433");
                upgradeDb = upgradeDb.replaceAll("pod 5432", "pod 5433\\:5432");
                FileUtils.write(Paths.get(UPGRADE_FOLDER, "steps", "upgrade_10_migrate_db").toFile(), upgradeDb, "UTF-8", false);
            }
        } catch (IOException e) {
            log.error("Unable to modify modify cli", e);
        }
    }

    private void copyStatefulScripts() {
        // Move the config change script to resource folder
        try {
            FileUtils.copyFile(new File("src/test/resources/upgrade/99-change-ui-config.sh"),
                Paths.get(UPGRADE_FOLDER, "migration", "resource", "99-change-ui-config.sh").toFile());
        } catch (IOException e) {
            fail("Unable to copy scripts", e);
        }
    }

    @Then("^verify successful test modifications$")
    public void verifySuccessfulTestModifications() {
        verifyTestModifications(false);
    }

    @Then("^verify test modifications rollback")
    public void verifyTestModificationsRollback() {
        verifyTestModifications(true);
    }

    @When("^add rollback cause to upgrade script")
    public void addRollbackCause() {
        System.setProperty("syndesis.upgrade.rollback", "");
        // Ideally this should be done in upgrade_60_restart_all but there is no rollback for that at the moment
        TestUtils.replaceInFile(Paths.get(UPGRADE_FOLDER, "steps", "upgrade_50_replace_template").toFile(),
            "update_version \\$tag", "update_version \\$tag; exit 1");
    }

    @Then("^wait until upgrade pod is finished$")
    public void waitForUpgrade() {
        Optional<Pod> pod = OpenShiftUtils.getPodByPartialName("syndesis-upgrade");
        int retries = 0;
        while (!pod.isPresent() && retries < 30) {
            TestUtils.sleepIgnoreInterrupt(5000L);
            retries++;
            pod = OpenShiftUtils.getPodByPartialName("syndesis-upgrade");
        }

        retries = 0;
        log.info("Waiting for syndesis-upgrade pod to finish");
        // 15 minutes
        while (!"Succeeded".equals(pod.get().getStatus().getPhase()) && retries < 180) {
            pod = OpenShiftUtils.getPodByPartialName("syndesis-upgrade");
            TestUtils.sleepIgnoreInterrupt(5000L);
            retries++;
        }
        if (retries == 180) {
            fail("Upgrade pod didn't finish in 15 minutes!");
        }
    }

    private void verifyTestModifications(boolean rollback) {
        // ConfigMap label change
        ConfigMap cm = OpenShiftUtils.getInstance().configMaps().withName("syndesis-ui-config").get();

        // New ENV variable in syndesis-server and syndesis-meta
        EnvVar dcEnvVar = null;
        DeploymentConfig dc = OpenShiftUtils.getInstance().deploymentConfigs().withName("syndesis-server").get();
        for (EnvVar envVar : dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv()) {
            if (envVar.getName().equals("TEST")) {
                dcEnvVar = envVar;
                break;
            }
        }

        final EnvVar finalDcEnvVar = dcEnvVar;

        if (rollback) {
            SoftAssertions.assertSoftly(softAssertions -> {
                softAssertions.assertThat(cm.getMetadata().getLabels().get("TEST")).isNull();
                softAssertions.assertThat(finalDcEnvVar).isNull();
                softAssertions.assertThat(integrationsEndpoint.get(integrationId).getName()).isEqualTo("upgrade");
                softAssertions.assertThat(integrationsEndpoint.get(integrationId).getDescription().get()).isEqualTo("Awkward integration.");
            });
        } else {
            SoftAssertions.assertSoftly(softAssertions -> {
                softAssertions.assertThat(cm.getMetadata().getLabels().get("TEST")).isEqualTo("UPGRADE");
                softAssertions.assertThat(finalDcEnvVar.getValue()).isEqualTo("UPGRADE");
                softAssertions.assertThat(integrationsEndpoint.get(integrationId).getName()).isEqualTo("UPGRADE INTEGRATION NAME");
                softAssertions.assertThat(integrationsEndpoint.get(integrationId).getDescription().get())
                    .isEqualTo("UPGRADE INTEGRATION DESCRIPTION");
            });
        }
    }

    private String getSyndesisVersion() {
        RestUtils.reset();
        Request request = new Request.Builder()
            .url(RestUtils.getRestUrl() + VERSION_ENDPOINT)
            .header("Accept", "text/plain")
            .build();
        try {
            return new OkHttpClient.Builder().build().newCall(request).execute().body().string();
        } catch (IOException e) {
            log.error("Unable to get version from " + VERSION_ENDPOINT);
            e.printStackTrace();
        }
        return null;
    }

    private void getSyndesisCli() {
        if (!Paths.get(UPGRADE_FOLDER, "syndesis-cli.jar").toFile().exists()) {
            log.info("Expecting to be run on jenkins, trying to copy ../../syndesis/app/server/cli/target/syndesis-cli.jar");
            try {
                FileUtils.copyFile(Paths.get("../../syndesis/app/server/cli/target/syndesis-cli.jar").toFile(),
                    Paths.get(UPGRADE_FOLDER, "syndesis-cli.jar").toFile());
            } catch (IOException e) {
                // If no artifacts were built, grab some cli from local maven repository
                log.warn("Unable to find cli in syndesis folder, trying local repository");
                File cli = FileUtils.listFiles(
                    Paths.get(System.getenv("HOME"), ".m2", "repository", "io",
                        "syndesis", "server", "syndesis-cli").toFile(),
                    new String[] {"jar"},
                    true)
                    .stream().filter(f -> f.getName().contains(System.getProperty("syndesis.upgrade.backup.version")))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unable to find jar for syndesis cli in local repository"));
                try {
                    FileUtils.copyFile(cli, Paths.get(UPGRADE_FOLDER, "syndesis-cli.jar").toFile());
                } catch (IOException ex) {
                    fail("Unable to copy syndesis-cli", ex);
                }
            }
        }
    }

    private void createFileFromTemplate(String folder, String templateFileName, String whatToReplace, String whatToUse) {
        File newFile = Paths.get(folder, templateFileName.replaceAll("-template", "")).toFile();
        try {
            FileUtils.copyFile(Paths.get(folder, templateFileName).toFile(), newFile);
        } catch (IOException e) {
            fail("Unable to copy template file", e);
        }
        TestUtils.replaceInFile(newFile, whatToReplace, whatToUse);
    }

    private void runShellInSyndesisHome(String... command) {
        log.info("Running \"" + String.join(" ", command) + "\" in \"" + SYNDESIS + "\"");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(SYNDESIS));

        try {
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
            p.waitFor(15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error while running script: ", e);
            e.printStackTrace();
        }
    }

    @Given("^clean upgrade modifications$")
    public void cleanUpgradeModifications() {
        runShellInSyndesisHome("git", "checkout", ".");
    }

    @Then("^verify correct s2i tag for builds$")
    public void verifyImageStreams() {
        OpenShiftUtils.getInstance().buildConfigs().list().getItems().stream()
            .filter(bc -> bc.getMetadata().getName().startsWith("i-"))
            .forEach(bc -> assertThat(bc.getSpec().getStrategy().getSourceStrategy().getFrom().getName()).contains(System.getProperty("TAG")));
    }

    @When("^delete buildconfig with name \"([^\"]*)\"$")
    public void deleteBc(String bc) {
        OpenShiftUtils.getInstance().buildConfigs().withName(bc).delete();
    }

    @Given("^delete syndesis operator$")
    public void deleteOperator() {
        OpenShiftUtils.getInstance().deploymentConfigs().withName("syndesis-operator").delete();
        Optional<ReplicationController> operatorRc = OpenShiftUtils.getInstance().replicationControllers().list().getItems().stream()
            .filter(rc -> rc.getMetadata().getName().startsWith("syndesis-operator")).findAny();
        operatorRc.ifPresent(rc -> OpenShiftUtils.getInstance().replicationControllers().withName(rc.getMetadata().getName()).delete());
        OpenShiftUtils.getPodByPartialName("syndesis-operator").ifPresent(
            pod -> OpenShiftUtils.getInstance().pods().withName(pod.getMetadata().getName()).delete()
        );
    }
}
