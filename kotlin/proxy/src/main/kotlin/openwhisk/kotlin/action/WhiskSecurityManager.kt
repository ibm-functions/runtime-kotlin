/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package openwhisk.kotlin.action

import java.security.Permission

/**
 * A `SecurityManager` installed when executing action code. The purpose here
 * is not so much to prevent malicious behavior than it is to prevent users from
 * shooting themselves in the foot. In particular, anything that kills the JVM
 * will result in unhelpful action error messages.
 */
class WhiskSecurityManager : SecurityManager() {

    override fun checkPermission(p: Permission) {
        // Not throwing means accepting anything.
    }

    override fun checkPermission(p: Permission, ctx: Any) {
        // Not throwing means accepting anything.
    }

    override fun checkExit(status: Int) {
        super.checkExit(status)
        throw SecurityException("System.exit(" + status + ") called from within an action.")
    }
}
