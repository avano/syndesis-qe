# @sustainer: avano@redhat.com

@rest
@integration-http
@integration-http-consumer
@http
@amqbroker
@activemq
Feature: Integration - HTTP
  Background:
    Given clean application state
      And deploy HTTP endpoints
      And deploy ActiveMQ broker
      And create ActiveMQ connection
      And create HTTP connection
      And create HTTPS connection

  @integration-http-consumer-get
  Scenario Outline: <protocol> GET to AMQ
    Given create <protocol> "GET" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-get"
    When create integration with name: "<protocol>-GET-AMQ"
      And configure keystore in <protocol> integration dc
    Then wait for integration with name: "<protocol>-GET-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "GET" was executed
      And verify that JMS message with content 'get' was received from "queue" "<protocol>-get"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-post
  Scenario Outline: <protocol> POST to AMQ
    Given create <protocol> "POST" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-post"
    When create integration with name: "<protocol>-POST-AMQ"
      And configure keystore in <protocol> integration dc 
    Then wait for integration with name: "<protocol>-POST-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "POST" was executed
      And verify that JMS message with content 'post' was received from "queue" "<protocol>-post"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-put
  Scenario Outline: <protocol> PUT to AMQ
    Given create <protocol> "PUT" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-put"
    When create integration with name: "<protocol>-PUT-AMQ"
      And configure keystore in <protocol> integration dc 
    Then wait for integration with name: "<protocol>-PUT-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "PUT" was executed
      And verify that JMS message with content 'put' was received from "queue" "<protocol>-put"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-delete
  Scenario Outline: <protocol> DELETE to AMQ
    Given create <protocol> "DELETE" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-delete"
    When create integration with name: "<protocol>-DELETE-AMQ"
      And configure keystore in <protocol> integration dc
    Then wait for integration with name: "<protocol>-DELETE-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "DELETE" was executed
      And verify that JMS message with content 'delete' was received from "queue" "<protocol>-delete"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-patch
  Scenario Outline: <protocol> PATCH to AMQ
    Given create <protocol> "PATCH" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-patch"
    When create integration with name: "<protocol>-PATCH-AMQ"
      And configure keystore in <protocol> integration dc 
    Then wait for integration with name: "<protocol>-PATCH-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "PATCH" was executed
      And verify that JMS message with content 'patch' was received from "queue" "<protocol>-patch"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-options
  Scenario Outline: <protocol> OPTIONS to AMQ
    Given create <protocol> "OPTIONS" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-options"
    When create integration with name: "<protocol>-OPTIONS-AMQ"
      And configure keystore in <protocol> integration dc
    Then wait for integration with name: "<protocol>-OPTIONS-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "OPTIONS" was executed
      And verify that JMS message with content 'options' was received from "queue" "<protocol>-options"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-trace
  Scenario Outline: <protocol> TRACE to AMQ
    Given create <protocol> "TRACE" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-trace"
    When create integration with name: "<protocol>-TRACE-AMQ"
      And configure keystore in <protocol> integration dc 
    Then wait for integration with name: "<protocol>-TRACE-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "TRACE" was executed
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-consumer-head
  Scenario Outline: <protocol> HEAD to AMQ
    Given create <protocol> "HEAD" step with period "5" "SECONDS"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-head"
    When create integration with name: "<protocol>-HEAD-AMQ"
      And configure keystore in <protocol> integration dc 
    Then wait for integration with name: "<protocol>-HEAD-AMQ" to become active
    When clear endpoint events
    Then verify that endpoint "HEAD" was executed
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |

  @integration-http-datamapper
  @datamapper
  Scenario Outline: <protocol> Response Datamapper
    Given create <protocol> "GET" step with path "/api/getXml" and period "5" "SECONDS"
      And change "out" datashape of previous step to "XML_INSTANCE" type with specification '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xmlResponse><dummyField1>x</dummyField1><dummyField2>y</dummyField2><method>get</method><dummyField3>z</dummyField3></xmlResponse>'
      And start mapper definition with name: "mapping 1"
      And MAP using Step 1 and field "/xmlResponse/method" to "/response/executedMethod"
      And create ActiveMQ "publish" action step with destination type "queue" and destination name "<protocol>-datamapper"
      And change "in" datashape of previous step to "XML_INSTANCE" type with specification '<response><executedMethod>TEST</executedMethod></response>'
    When create integration with name: "<protocol>-GET-AMQ-Datamapper"
      And configure keystore in <protocol> integration dc
    Then wait for integration with name: "<protocol>-GET-AMQ-Datamapper" to become active
    When clear endpoint events
    Then verify that endpoint "GET" was executed
      And verify that JMS message with content '<?xml version="1.0" encoding="UTF-8" standalone="no"?><response><executedMethod>get</executedMethod></response>' was received from "queue" "<protocol>-datamapper"
    Examples:
      | protocol |
      | HTTP     |
      | HTTPS    |
