package mabh.vertx;
/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import java.io.File;

import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.platform.Verticle;

public final class ChatServerVerticle extends Verticle {

  public void start() {
	container.logger().info("ChatServerVerticle started in thread - " + Thread.currentThread().getId());

	//web server
	RouteMatcher httpRouteMatcher = new RouteMatcher().get("/", request -> {
				request.response().sendFile("web/client.html");
			}).get(".*\\.(css|js)$", request -> {
					request.response().sendFile("web/" + new File(request.path()));
			});
	vertx.createHttpServer().requestHandler(httpRouteMatcher).listen(12080, "localhost");
	
	//web socket server
	vertx.createHttpServer().websocketHandler(sws -> {
		/*
		 * handle websocket interactions
		 */
		container.logger().info("path: " + sws.path());
		if(!sws.path().equals("/chat")) {
			sws.reject();
			return;
		}
		
		/*
		 * web socket id. A topic with the name = swsId is created in event bus
		 * if client1 writes to that topic, message is sent to the clientX attached to that websocket
		 * maintain a shared set of web socket IDs
		 */
		final String swsId = sws.textHandlerID();
		vertx.sharedData().getSet("chat.room").add(swsId);
		container.logger().info("socket added to shared data: " + swsId);
		
		/*
		 * on close
		 */
		sws.closeHandler(argument -> {
			vertx.sharedData().getSet("chat.room").remove(swsId);
			container.logger().info("socket removed from shared data: " + swsId);
		});
		
		/*
		 * on receiving data
		 */
		sws.dataHandler(buffer -> {
			container.logger().info("data received on the socket:" + swsId);
			for(Object socketId: vertx.sharedData().getSet("chat.room")) {
				//dont send to self message
				if(!swsId.equals(socketId)) {
					vertx.eventBus().send((String)socketId, buffer.toString());
					container.logger().info("sending message " + buffer.toString() + " to " + socketId);
				}
			}
		});
	}).listen(12081, "localhost");
	
    container.logger().info("ChatServerVerticle started");
  }
}
