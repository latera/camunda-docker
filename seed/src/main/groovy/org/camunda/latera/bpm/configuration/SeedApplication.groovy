package org.camunda.latera.bpm.configuration

import org.camunda.bpm.application.PostDeploy
import org.camunda.bpm.application.PreUndeploy
import org.camunda.bpm.application.ProcessApplication
import org.camunda.bpm.application.impl.ServletProcessApplication
import org.camunda.latera.bss.logging.SimpleLogger
import org.camunda.bpm.engine.AuthorizationService
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.authorization.Groups
import org.camunda.bpm.engine.authorization.Resource
import org.camunda.bpm.engine.authorization.Resources
import org.camunda.bpm.engine.identity.Group
import org.camunda.bpm.engine.identity.GroupQuery
import org.camunda.bpm.engine.identity.User
import org.camunda.bpm.engine.identity.UserQuery
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity

import static org.camunda.bpm.engine.authorization.Authorization.ANY
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT
import static org.camunda.bpm.engine.authorization.Groups.CAMUNDA_ADMIN
import static org.camunda.bpm.engine.authorization.Permissions.ALL
@ProcessApplication('seed')
class SeedApplication extends ServletProcessApplication {
  @PostDeploy
  public void createAdminUser(ProcessEngine processEngine) {
    def ENV = System.getenv()
    Boolean seed = Boolean.valueOf(ENV['DB_SEED'] ?: 'true')

    if (seed) {
      def logger = new SimpleLogger()
      IdentityService identityService = processEngine.getIdentityService()
      AuthorizationService authorizationService = processEngine.getAuthorizationService()

      String adminName = ENV['ADMIN_USERNAME'] ?: 'user'
      String adminPassword  = ENV['ADMIN_PASSWORD'] ?: 'changeme'
      String adminEmail     = ENV['ADMIN_EMAIL'] ?: 'user@example.com'
      String adminFirstName = ENV['ADMIN_FIRST_NAME'] ?: 'Super'
      String adminLastName  = ENV['ADMIN_LAST_NAME'] ?: 'Admin'

      if(adminName != null) {
        User singleResult = identityService.createUserQuery().userId(adminName).singleResult()
        if (singleResult != null) {
          return
        }

        logger.info("Creating admin user")

        User admin = identityService.newUser(adminName)
        admin.setFirstName(adminFirstName)
        admin.setLastName(adminLastName)
        admin.setPassword(adminPassword)
        admin.setEmail(adminEmail)
        identityService.saveUser(admin)

        // create group
        if(identityService.createGroupQuery().groupId(CAMUNDA_ADMIN).count() == 0) {
          Group adminGroup = identityService.newGroup(CAMUNDA_ADMIN)
          adminGroup.setName("camunda BPM Administrators")
          adminGroup.setType(Groups.GROUP_TYPE_SYSTEM)
          identityService.saveGroup(adminGroup)
        }

        // create ADMIN authorizations on all built-in resources
        for (Resource resource : Resources.values()) {
          if(authorizationService.createAuthorizationQuery().groupIdIn(CAMUNDA_ADMIN).resourceType(resource).resourceId(ANY).count() == 0) {
            AuthorizationEntity adminAuth = new AuthorizationEntity(AUTH_TYPE_GRANT)
            adminAuth.setGroupId(CAMUNDA_ADMIN)
            adminAuth.setResource(resource)
            adminAuth.setResourceId(ANY)
            adminAuth.addPermission(ALL)
            authorizationService.saveAuthorization(adminAuth)
          }
        }

        identityService.createMembership(adminName, CAMUNDA_ADMIN)
      }
    }
  }
}
