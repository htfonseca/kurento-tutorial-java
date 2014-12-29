/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package org.kurento.tutorial.magicmirror;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.CodeFoundEvent;
import org.kurento.client.EventListener;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.ZBarFilter;
import org.kurento.client.factory.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Magic Mirror handler (application and media logic).
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class MagicMirrorHandler extends TextWebSocketHandler {

	private final Logger log = LoggerFactory
			.getLogger(MagicMirrorHandler.class);
	private static final Gson gson = new GsonBuilder().create();
	private ConcurrentHashMap<String, MediaPipeline> pipelines = new ConcurrentHashMap<String, MediaPipeline>();

	@Autowired
	private KurentoClient kurento;
	@Autowired
	private ConcurrentHashMap<String, WebSocketSession> kurentoSession;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);

		log.debug("Incoming message: {}", jsonMessage);

		switch (jsonMessage.get("id").getAsString()) {
		case "start":
			start(session, jsonMessage);
			break;

		case "stop":
			String sessionId = session.getId();
			if (pipelines.containsKey(sessionId)) {
				pipelines.get(sessionId).release();
				pipelines.remove(sessionId);
			}
			if (kurentoSession.containsKey(sessionId)) {
				kurentoSession.remove(sessionId);
			}
			break;

		default:
			sendError(session,
					"Invalid message with id "
							+ jsonMessage.get("id").getAsString());
			break;
		}
	}

	private void start(final WebSocketSession session, JsonObject jsonMessage) {
		try {
			// Media Logic (Media Pipeline and Elements)
			MediaPipeline pipeline = kurento.createMediaPipeline();
			pipelines.put(session.getId(), pipeline);
			kurentoSession.put(session.getId(), session);

			WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline)
					.build();

			// ZBarFilter
			ZBarFilter zBarFilter = new ZBarFilter.Builder(pipeline).build();
			zBarFilter
					.addCodeFoundListener(new EventListener<CodeFoundEvent>() {
						@Override
						public void onEvent(CodeFoundEvent event) {
							log.debug("Event Found " + event.toString());
							
							// Sending response back to client
							JsonObject response = new JsonObject();
							response.addProperty("id", "zbarcode");
							response.addProperty("code", event.getCodeType());
							response.addProperty("type", event.getType());
							response.addProperty("value", event.getValue());
							sendMessage(session,
									new TextMessage(response.toString()));

							// Sending response to CEP
							JsonObject responseCEP = new JsonObject();
							responseCEP.addProperty("Name","KurentoQRCodeEvent");
							responseCEP.addProperty("source", session.getId());
							responseCEP.addProperty("type", event.getCodeType());
							responseCEP.addProperty("value", event.getValue());
							sendRestMessage(responseCEP.toString());
						}
					});

			webRtcEndpoint.connect(zBarFilter);
			zBarFilter.connect(webRtcEndpoint);

			// SDP negotiation (offer and answer)
			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

			// Sending response back to client
			JsonObject response = new JsonObject();
			response.addProperty("id", "startResponse");
			response.addProperty("sdpAnswer", sdpAnswer);
			sendMessage(session, new TextMessage(response.toString()));

		} catch (Throwable t) {
			sendError(session, t.getMessage());
		}
	}

	private void sendRestMessage(String JSonMessage) {

		try {

			Client client = Client.create();

			WebResource webResource = client
					.resource(System.getProperty("DEFAULT_CEP_HTTP_URI"));

			//String input = "{\"singer\":\"Metallica\",\"title\":\"Fade To Black\"}";

			ClientResponse response = webResource.type("application/json")
					.post(ClientResponse.class, JSonMessage);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ response.getStatus());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void sendMessage(WebSocketSession session, TextMessage message) {
		try {
			session.sendMessage(message);
		} catch (Throwable t) {
			sendError(session, t.getMessage());
		}
	}

	private void sendError(WebSocketSession session, String message) {
		try {
			JsonObject response = new JsonObject();
			response.addProperty("id", "error");
			response.addProperty("message", message);
			session.sendMessage(new TextMessage(response.toString()));
		} catch (IOException e) {
			log.error("Exception sending message", e);
		}
	}
}
