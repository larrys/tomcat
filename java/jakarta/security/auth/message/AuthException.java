/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message;

import java.io.Serial;

import javax.security.auth.login.LoginException;

public class AuthException extends LoginException {
    @Serial
    private static final long serialVersionUID = -1156951780670243758L;

    public AuthException() {
    }

    public AuthException(String msg) {
        super(msg);
    }

    /**
     * Construct an instance of AuthException.
     *
     * @param msg   Exception message
     * @param cause The cause of the exception
     *
     * @since Authentication 3.0
     */
    public AuthException(String msg, Throwable cause) {
        super(msg);
        initCause(cause);
    }

    /**
     * Construct an instance of AuthException.
     *
     * @param cause The cause of the exception
     *
     * @since Authentication 3.0
     */
    public AuthException(Throwable cause) {
        initCause(cause);
    }
}
