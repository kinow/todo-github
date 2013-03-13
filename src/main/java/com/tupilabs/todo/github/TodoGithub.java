package com.tupilabs.todo.github;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;

public class TodoGithub {
	
	private static final Logger LOGGER = Logger.getLogger("todo.github");

	private static final String INPUT = "/github_users_orgs";
	
	public static void main(String[] args) throws Exception {
		File output = new File("/tmp/todo-" + System.nanoTime() + "/");
		output.mkdirs();
		
		final String[] usersAndOrganizations = FileUtils.readFileToString(new File(TodoGithub.class.getResource(INPUT).getFile())).split("\n");
		
		final ClientConfig cc = new DefaultClientConfig();
	    cc.getProperties().put(
	        ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
	    cc.getProperties().put(
		        ApacheHttpClientConfig.PROPERTY_PROXY_URI, "http://localhost:3128");
	    final Client c = ApacheHttpClient.create(cc);
	    
		for (String userOrOrg : usersAndOrganizations) {
			final WebResource r = c.resource("https://api.github.com/users/" + userOrOrg+"/repos");
			LOGGER.info(r.toString());
			String response = r.accept(
			        MediaType.APPLICATION_JSON_TYPE,
			        MediaType.APPLICATION_XML_TYPE).
			        get(String.class);
			
			JsonFactory jfactory = new JsonFactory();
			JsonParser jParser = jfactory.createJsonParser(response);
			
			String name = null;
			String htmlUrl = null;
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				if ("name".equals(fieldName)) {
					name = jParser.getText();
				} else if ("html_url".equals(fieldName)) {
					htmlUrl = "https://github.com/" + userOrOrg + "/" + name; 
				}
			}
			
			// git clone html_url
			LOGGER.info("Cloning name ["+name+"] url ["+htmlUrl+"]");
			String command = "git clone " + htmlUrl + " " + output.getAbsolutePath() + "/" + name;
			execute(command);
			
			// git grep TODO, FIXME, TBD, todo, fixme, tbd, 'we have to', 'we need to', todo, fixme, tbd
			
			LOGGER.info("Greping...");
			command = "git grep TODO";
			String result = execute(command);
			
			System.out.println(result);
			
			// TODO: parse the below line and store the file
			// file = access-modifier-checker/src/main/java/org/kohsuke/accmod/impl/Checker.java:
			// TODO: append to database: 
			// https://github.com/<user>/<name>/blob/master/<file>
			
			break;
		}
		System.exit(0);
	}
	
	public static String execute(String command) throws ExecuteException, IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CommandLine cmdLine = CommandLine.parse(command);
		DefaultExecutor executor = new DefaultExecutor();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		executor.setStreamHandler(streamHandler);
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);
		int exitValue = executor.execute(cmdLine);
		
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine("output text: " + outputStream.toString());
			LOGGER.fine("exit status: " + exitValue);
		}
		return outputStream.toString();
	}
	
}
