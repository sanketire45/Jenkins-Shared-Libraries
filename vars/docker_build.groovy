// Define function
def call(String ProjectName, String ImageTag, String DockerHubUser){
  sh "docker build --provenance=false -t ${DockerHubUser}/${ProjectName}:${ImageTag} ."
}
