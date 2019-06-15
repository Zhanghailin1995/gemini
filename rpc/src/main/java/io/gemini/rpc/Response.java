/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.rpc;

import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.transport.Status;
import io.gemini.transport.payload.ResponsePayload;

/**
 * Provider's response data.
 *
 * 响应信息载体.
 *
 * jupiter
 * org.jupiter.rpc
 *
 * @author jiachun.fjc
 */
public class Response {

    private final ResponsePayload payload;     // 响应bytes/stream
    private ResultWrapper result;               // 响应对象

    public Response(long id) {
        payload = new ResponsePayload(id);
    }

    public Response(ResponsePayload payload) {
        this.payload = payload;
    }

    public ResponsePayload payload() {
        return payload;
    }

    public long id() {
        return payload.id();
    }

    public byte status() {
        return payload.status();
    }

    public void status(byte status) {
        payload.status(status);
    }

    public void status(Status status) {
        payload.status(status.value());
    }

    public byte serializerCode() {
        return payload.serializerCode();
    }

    public ResultWrapper result() {
        return result;
    }

    public void result(ResultWrapper result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "Response{" +
                "status=" + Status.parse(status()) +
                ", id=" + id() +
                ", result=" + result +
                '}';
    }
}
