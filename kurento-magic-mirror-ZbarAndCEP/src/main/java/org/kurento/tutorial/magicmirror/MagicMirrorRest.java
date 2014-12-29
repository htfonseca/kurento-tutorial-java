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
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/**
 * Hello World REST Controller (application logic).
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
@RestController
public class MagicMirrorRest {

	private final Logger log = LoggerFactory.getLogger(MagicMirrorRest.class);

	@Autowired
	public ConcurrentHashMap<String, WebSocketSession> kurentoSession;

	@RequestMapping(value = "/message", method = RequestMethod.POST)
	private String processRequest(@RequestBody String bMessage) {

		log.debug("New REST message " + bMessage);
		
		JsonObject response = createJson(bMessage);
		for (Map.Entry<String, WebSocketSession> element : kurentoSession
				.entrySet()) {
			try {
				element.getValue().sendMessage(
						new TextMessage(response.toString()));
			} catch (IOException e) {
				log.error("Exception sending message in processRequest", e);
			}
		}

		return kurentoSession.toString();
	}

	private JsonObject createJson(String bMessage) {

		JsonReader reader = new JsonReader(new StringReader(bMessage));
		reader.setLenient(true);
		JsonObject request = (JsonObject) new JsonParser().parse(reader);

		JsonObject response = new JsonObject();
		response.addProperty("id", "rest");
		response.add("source", request.get("source"));
		response.add("description", request.get("description"));
		response.add("value", request.get("value"));

		return response;
	}
}