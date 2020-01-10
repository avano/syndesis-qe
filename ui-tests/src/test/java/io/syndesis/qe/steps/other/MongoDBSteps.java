package io.syndesis.qe.steps.other;

import io.syndesis.qe.accounts.Account;
import io.syndesis.qe.accounts.AccountsDirectory;
import io.syndesis.qe.resource.impl.MongoDb36;
import io.syndesis.qe.utils.OpenShiftUtils;
import io.syndesis.qe.utils.TestUtils;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.LocalPortForward;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoDBSteps {

    private MongoClient client;
    private Account mongoAccount;
    private MongoDatabase database;
    private LocalPortForward portForward;

    @Given("connect to MongoDB {string}")
    public void connectToMongoDB(String mongodDb) {
        mongoAccount = AccountsDirectory.getInstance().getAccount(mongodDb).get();
        createPortForward();
        client = MongoClients.create(mongoAccount.getProperty("url").replace("@mongodb", "@localhost"));
        database = client.getDatabase(mongoAccount.getProperty("database"));
    }

    private void createPortForward() {
        try {
            Pod mongoPod = OpenShiftUtils.getInstance().getAnyPod("app", mongoAccount.getService());
            portForward = TestUtils.createLocalPortForward(mongoPod, MongoDb36.MONGODB_PORT, MongoDb36.MONGODB_PORT);
        } catch (Exception e) {
            // we don't fail here because we might be running the port forward locally when debugging
            log.error("could not port forward, mongo connections probably won't work", e);
        }
    }

    @When("create mongodb collection {string}")
    public void createCollection(String collectionName) {
        database.getCollection(collectionName).drop();
        database.createCollection(collectionName);
    }

    @Given("create mongodb capped collection {string} with size {int} and max {int}")
    public void createCappedCollection(String collectionName, int size, int max) {
        database.getCollection(collectionName).drop();
        CreateCollectionOptions options = new CreateCollectionOptions()
            .capped(true)
            .sizeInBytes(size)
            .maxDocuments(max);
        database.createCollection(collectionName, options);
    }

    @When("insert the following documents into mongodb collection {string}")
    public void insertDocuments(String collectionName, DataTable documents) {
        documents.<String, Object>asMaps(String.class, String.class).forEach(d ->
            database.getCollection(collectionName).insertOne(new Document(d))
        );
    }

    @Then("verify that mongodb collection {string} has {int} document matching")
    public void verifyMongoDocuments(String collectionName, int count, DataTable find) {
        Document findSpec = new Document(find.<String, Object>asMaps(String.class, String.class).get(0));

        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .pollDelay(Duration.ofSeconds(3))
            .pollInterval(Duration.ofSeconds(3))
            .untilAsserted(() -> {
                FindIterable<Document> documents = database.getCollection(collectionName).find(findSpec);
                List<Document> results = new ArrayList<>();
                documents.forEach((Consumer<? super Document>) results::add);
                Assertions
                    .assertThat(results)
                    .describedAs("Didn't find the requested document %s in collection %s", findSpec, collectionName)
                    .hasSize(count);
            });
    }

    @After("@mongodb")
    public void closeMongoConnection() {
        if (client != null) {
            client.close();
        }
        if (portForward != null) {
            log.info("closing mongodb port forward");
            try {
                portForward.close();
            } catch (IOException e) {
                log.error("Could not close port forward", e);
            }
        }
    }
}
