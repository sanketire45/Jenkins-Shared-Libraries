// ecr_push.groovy — ECR login + docker push via STS assume-role
//
// Why assume-role instead of using the instance role directly:
//   Instance role only needs sts:AssumeRole (tiny blast radius).
//   CartOps-Jenkins-CI-Role holds the actual ECR permissions.
//   Temp creds last 1 hr, and the named profile is scoped per build
//   so concurrent builds never overwrite each other's credentials.

def call(String repoName, String imageTag, String region, String ecrRegistry) {

    // Profile includes repoName so 4 parallel stages don't write to the same
    // profile simultaneously and corrupt ~/.aws/credentials
    def buildProfile = "${env.JOB_NAME}_${env.BUILD_NUMBER}_${repoName}".replaceAll('[^A-Za-z0-9_-]', '_')
    def roleArn      = env.ECR_ROLE_ARN   // set in pipeline environment block

    sh """
        set -e

        # ── 1. STS assume-role → get short-lived credentials ──────────────────
        #    Instance role calls assume-role; actual ECR permissions live in the
        #    assumed role, NOT directly on the instance profile.
        ASSUMED=\$(aws sts assume-role \\
            --role-arn ${roleArn} \\
            --role-session-name cartops-ecr-${env.BUILD_NUMBER} \\
            --output json)

        KEY=\$(echo "\$ASSUMED"    | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["AccessKeyId"])')
        SECRET=\$(echo "\$ASSUMED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["SecretAccessKey"])')
        TOKEN=\$(echo "\$ASSUMED"  | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["SessionToken"])')

        # ── 2. Write creds into a build-isolated named profile ─────────────────
        #    Profile = JOB_BUILD — two parallel builds never stomp each other
        aws configure set aws_access_key_id     "\$KEY"    --profile ${buildProfile}
        aws configure set aws_secret_access_key "\$SECRET" --profile ${buildProfile}
        aws configure set aws_session_token     "\$TOKEN"  --profile ${buildProfile}
        aws configure set region                ${region}  --profile ${buildProfile}

        # ── 3. ECR login using the assumed-role profile ────────────────────────
        aws ecr get-login-password --region ${region} --profile ${buildProfile} | \\
            docker login --username AWS --password-stdin ${ecrRegistry}

        # ── 4. Push image ──────────────────────────────────────────────────────
        docker push ${ecrRegistry}/${repoName}:${imageTag}

        # ── 5. Wipe profile — creds expire in 1 hr anyway, defence-in-depth ───
        aws configure set aws_access_key_id     "" --profile ${buildProfile}
        aws configure set aws_secret_access_key "" --profile ${buildProfile}
        aws configure set aws_session_token     "" --profile ${buildProfile}
    """
}
