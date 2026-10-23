// trivy_image_scan.groovy — scans a built Docker image for known CVEs
//
// Called AFTER docker build, BEFORE ECR push.
// Fails the pipeline on any CRITICAL or HIGH vulnerability so nothing
// broken reaches the registry.

def call(String imageName) {
    // --skip-db-update: DB already downloaded in trivy_scan (filesystem stage).
    // Avoids write-lock contention when 4 parallel image scans run simultaneously.
    sh "trivy image --exit-code 1 --severity CRITICAL,HIGH --no-progress --skip-db-update ${imageName}"
}
