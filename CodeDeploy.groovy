@Grapes([
	@Grab(group='com.amazonaws', module='aws-java-sdk', version='1.10.29')

])
@Grab(group='log4j', module='log4j', version='1.2.17')

import com.amazonaws.services.codedeploy.AmazonCodeDeployClient
import com.amazonaws.services.codedeploy.model.*
import com.amazonaws.regions.*
import org.apache.log4j.*
import groovy.transform.TimedInterrupt
import java.util.concurrent.TimeUnit

@TimedInterrupt(value = 120L, unit = TimeUnit.SECONDS)
def logic(){
	// setup logging
	ConsoleAppender console = new ConsoleAppender()
	String PATTERN = "%d [%p|%c|%C{1}] %m%n";
	console.setLayout(new PatternLayout(PATTERN));
	console.activateOptions()
	def rootLogger = Logger.getRootLogger()
	rootLogger.addAppender(console)
	rootLogger.setLevel(Level.DEBUG)

	// CodeDeploy calls
	def amazonCodeDeployClient = new AmazonCodeDeployClient()

	amazonCodeDeployClient.setRegion(Region.getRegion(Regions.valueOf(project.properties['region'])));
	def createDeploymentRequest = new CreateDeploymentRequest()

	createDeploymentRequest.withApplicationName(project.properties['application-name'])
	//createDeploymentRequest.withDeploymentConfigName('CodeDeployDefault.OneAtATime')
	createDeploymentRequest.withDeploymentGroupName(project.properties['deployment-group-name'])
	createDeploymentRequest.withDescription('CodeDeploy '+project.properties['targeted.project']+' app for env '+project.properties['targeted.env'])
	// If set to true, then if the deployment causes the ApplicationStop deployment lifecycle event to fail to a specific instance, the deployment will not be considered to have failed to that instance at that point and will continue on to the BeforeInstall deployment lifecycle event.
	//createDeploymentRequest.withIgnoreApplicationStopFailures(false)

	def revisionLocation = new RevisionLocation()
	revisionLocation.withRevisionType(RevisionLocationType.S3)

	def s3Location = new S3Location()
	s3Location.withBucket(project.properties['s3-bucket-name'])
	s3Location.withBundleType(BundleType.Zip)
	s3Location.withKey(project.properties['zip-name'])
	//s3Location.withETag('d22124c77f6f66b0fb007f769d5ca46d')
	revisionLocation.withS3Location(s3Location)
	createDeploymentRequest.withRevision(revisionLocation)
	def deploymentId = amazonCodeDeployClient.createDeployment(createDeploymentRequest)['deploymentId']
	rootLogger.info('deploymentId='+deploymentId);
	def getDeploymentRequest = new GetDeploymentRequest().withDeploymentId(deploymentId)

	def deploymentStatus = null
	def statusEndStates = EnumSet.of(DeploymentStatus.Failed, DeploymentStatus.Stopped, DeploymentStatus.Succeeded )

	while (!statusEndStates.contains(deploymentStatus)){
		sleep(2000)
		def deploymentInfo = amazonCodeDeployClient.getDeployment(getDeploymentRequest).getDeploymentInfo()
		deploymentStatus =  DeploymentStatus.valueOf(deploymentInfo.getStatus())
	}
	
	rootLogger.info('Deployment has '+deploymentStatus)

	if (deploymentStatus != DeploymentStatus.Succeeded){
		fail()
	}
}

logic()




