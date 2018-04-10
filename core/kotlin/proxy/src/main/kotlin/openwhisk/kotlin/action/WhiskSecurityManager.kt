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