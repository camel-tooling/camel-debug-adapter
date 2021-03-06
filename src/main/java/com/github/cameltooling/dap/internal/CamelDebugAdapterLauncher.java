/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cameltooling.dap.internal;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

public class CamelDebugAdapterLauncher {

	public static void main(String[] args) {
		CamelDebugAdapterServer debugServer = new CamelDebugAdapterServer();
		Launcher<IDebugProtocolClient> serverLauncher = DSPLauncher.createServerLauncher(debugServer, System.in, System.out);
		IDebugProtocolClient clientProxy = serverLauncher.getRemoteProxy();
		debugServer.connect(clientProxy);
		serverLauncher.startListening();
	}

}
