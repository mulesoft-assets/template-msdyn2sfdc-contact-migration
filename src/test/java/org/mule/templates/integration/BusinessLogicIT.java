/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.InterceptingChainLifecycleWrapper;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is validating the correct behavior of the flows
 * for this Mule Anypoint Template
 * 
 */
@SuppressWarnings("unchecked")
public class BusinessLogicIT extends AbstractTemplateTestCase {

	private static final String INBOUND_FLOW_NAME = "triggerFlow";
	private static final String ANYPOINT_TEMPLATE_PREFIX = "msdyn2sfdc-contact-migr";

	private static final String OBJ_ID = "Id";
	private static final String NAME = "Name";
	private static final String FIRST_NAME = "FirstName";
	private static final String LAST_NAME = "LastName";
	private static final String EMAIL = "Email";
	private static final String PHONE = "Phone";
	private static final String ACCOUNT_ID = "AccountId";

	private static final int TIMEOUT_MILLIS = 60;
	private BatchTestHelper batchTestHelper;
	private String anypointTemplateName = "";

	private static List<Map<String, Object>> contactsCreatedInMsDynamics = new ArrayList<Map<String, Object>>();
	private static List<Map<String, Object>> accountsCreatedInMsDynamics = new ArrayList<Map<String, Object>>();

	private static List<String> contactsCreatedInSalesforce = new ArrayList<String>();
	private static List<String> accountsCreatedInSalesforce = new ArrayList<String>();

	private static SubflowInterceptingChainLifecycleWrapper createAccountInMsDynamicsFlow;
	private static SubflowInterceptingChainLifecycleWrapper createContactInMsDynamicsFlow;
	private static SubflowInterceptingChainLifecycleWrapper deleteAccountFromMsDynamicsFlow;
	private static SubflowInterceptingChainLifecycleWrapper deleteContactFromMsDynamicsFlow;
	private static SubflowInterceptingChainLifecycleWrapper queryContactFromSalesforceFlow;
	private static SubflowInterceptingChainLifecycleWrapper deleteObjectFromSalesforceFlow;

	@BeforeClass
	public static void beforeTestClass() {
		System.setProperty("account.sync.policy", "syncAccount");
	}

	@Before
	public void setUp() throws Exception {
		getAndInitializeFlows();
		batchTestHelper = new BatchTestHelper(muleContext);

		anypointTemplateName = ANYPOINT_TEMPLATE_PREFIX + "_" + System.currentTimeMillis();

		Map<String, Object> account = new HashMap<String, Object>();
		account.put(NAME, anypointTemplateName);

		Map<String, Object> createdAccount = createTestObjectInMsDynamics(account, createAccountInMsDynamicsFlow);
		accountsCreatedInMsDynamics.add(createdAccount);

		Map<String, Object> contact = new HashMap<String, Object>();
		contact.put(FIRST_NAME, "fn_" + anypointTemplateName);
		contact.put(LAST_NAME, "ln_" + anypointTemplateName);
		contact.put(EMAIL, anypointTemplateName + "@msdyn.com");
		contact.put(PHONE, "123-4567");
		contact.put(ACCOUNT_ID, createdAccount.get(OBJ_ID));

		Map<String, Object> createdContact = createTestObjectInMsDynamics(contact, createContactInMsDynamicsFlow);
		contactsCreatedInMsDynamics.add(createdContact);
	}

	private void getAndInitializeFlows() throws InitialisationException {
		// Flow for creating accounts in MS Dynamics instance
		createAccountInMsDynamicsFlow = getSubFlow("createAccountInMsDynamicsFlow");
		createAccountInMsDynamicsFlow.initialise();

		// Flow for creating contacts in MS Dynamics instance
		createContactInMsDynamicsFlow = getSubFlow("createContactInMsDynamicsFlow");
		createContactInMsDynamicsFlow.initialise();

		// Flow for deleting accounts in MS Dynamics instance
		deleteAccountFromMsDynamicsFlow = getSubFlow("deleteAccountFromMsDynamicsFlow");
		deleteAccountFromMsDynamicsFlow.initialise();

		// Flow for deleting contacts in MS Dynamics instance
		deleteContactFromMsDynamicsFlow = getSubFlow("deleteContactFromMsDynamicsFlow");
		deleteContactFromMsDynamicsFlow.initialise();

		// Flow for querying contacts in Salesforce instance
		queryContactFromSalesforceFlow = getSubFlow("queryContactFromSalesforceFlow");
		queryContactFromSalesforceFlow.initialise();

		// Flow for deleting objects in Salesforce instance
		deleteObjectFromSalesforceFlow = getSubFlow("deleteObjectFromSalesforceFlow");
		deleteObjectFromSalesforceFlow.initialise();
	}

	@Test
	public void testMainFlow() throws MuleException, Exception {
		// Execution
		runFlow(INBOUND_FLOW_NAME);
		executeWaitAndAssertBatchJob(INBOUND_FLOW_NAME);

		// query for just created contacts in Salesforce
		for (Map<String, Object> contactInMsDynamics : contactsCreatedInMsDynamics) {
			Map<String, Object> contactInSalesforce = new HashMap<String, Object>();
			contactInSalesforce.put(FIRST_NAME, contactInMsDynamics.get(FIRST_NAME));
			contactInSalesforce.put(LAST_NAME, contactInMsDynamics.get(LAST_NAME));
			Map<String, Object> response = (Map<String, Object>) queryContactFromSalesforceFlow
					.process(getTestEvent(contactInSalesforce, MessageExchangePattern.REQUEST_RESPONSE)).getMessage()
					.getPayload();

			Assert.assertNotNull("There should be a contact created", response.get(NAME));
			Assert.assertTrue("Contact FirstName should match",
					response.get(FIRST_NAME).equals(contactInMsDynamics.get(FIRST_NAME)));
			Assert.assertTrue("Contact LastName should match", response.get(LAST_NAME).equals(contactInMsDynamics.get(LAST_NAME)));
			Assert.assertTrue("Contact Email should match", response.get(EMAIL).equals(contactInMsDynamics.get(EMAIL)));
			Assert.assertTrue("Contact Phone should match", response.get(PHONE).equals(contactInMsDynamics.get(PHONE)));
			contactsCreatedInSalesforce.add((String) response.get(OBJ_ID));

			Map<String, Object> relevantAccountInMsDynamics = new HashMap<String, Object>();

			for (Map<String, Object> accountInMsDynamics : accountsCreatedInMsDynamics) {
				if (accountInMsDynamics.get(OBJ_ID).equals(contactInMsDynamics.get(ACCOUNT_ID))) {
					relevantAccountInMsDynamics = accountInMsDynamics;
					break;
				}
			}

			Assert.assertNotNull("There should be an account created for contact", response.get("Account"));
			Map<String, Object> account = (HashMap<String, Object>) response.get("Account");
			Assert.assertTrue("Account name does not match", account.get(NAME).equals(relevantAccountInMsDynamics.get(NAME)));
			accountsCreatedInSalesforce.add((String) response.get(ACCOUNT_ID));
		}
	}

	private Map<String, Object> createTestObjectInMsDynamics(Map<String, Object> testObject,
			InterceptingChainLifecycleWrapper createtestObjectFlow) throws MuleException, Exception {
		Object payload = createtestObjectFlow.process(getTestEvent(testObject, MessageExchangePattern.REQUEST_RESPONSE))
				.getMessage().getPayload();

		testObject.put(OBJ_ID, payload);
		return testObject;
	}

	@AfterClass
	public static void shutDown() {
		System.clearProperty("account.sync.policy");
	}

	@After
	public void tearDown() throws MuleException, Exception {
		cleanUpSandboxesByRemovingTestContacts();
	}

	private static void cleanUpSandboxesByRemovingTestContacts() throws MuleException, Exception {
		for (Map<String, Object> contact : contactsCreatedInMsDynamics) {
			deleteContactFromMsDynamicsFlow.process(getTestEvent(contact.get(OBJ_ID), MessageExchangePattern.REQUEST_RESPONSE));
		}

		for (Map<String, Object> account : accountsCreatedInMsDynamics) {
			deleteAccountFromMsDynamicsFlow.process(getTestEvent(account.get(OBJ_ID), MessageExchangePattern.REQUEST_RESPONSE));
		}

		List<String> objectIds = new ArrayList<String>();

		for (String accountId : accountsCreatedInSalesforce) {
			objectIds.add(accountId);
		}

		for (String contactId : contactsCreatedInSalesforce) {
			objectIds.add(contactId);
		}

		deleteObjectFromSalesforceFlow.process(getTestEvent(objectIds, MessageExchangePattern.REQUEST_RESPONSE));
	}

	private void executeWaitAndAssertBatchJob(String flowConstructName) throws Exception {

		// Execute synchronization
		runSchedulersOnce(flowConstructName);

		// Wait for the batch job execution to finish
		batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
		batchTestHelper.assertJobWasSuccessful();
	}
}
