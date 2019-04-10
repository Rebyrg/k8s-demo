# Application
It is a very simple spring boot application created for demonstration of basic kubernetes features.
It also shows basic principles of creating spring boot applications which are deployed on a kubernetes cluster.

The application exposes only one simple service `/rate` on spring boot http port (default 8080).
The rate service returns a rate for a pair of currencies, e.g. it returns 1.0 value for EUR/USD. 
The rate service works in two modes, each created for a different purpose.

1. Checking if application is serving proper output

When service is executed with EUR/USD pair it returns a constant value defined in attribute `eurUsdRate` 
of `CurrencyProviderRest` class.
It is normal, fast operation with low latency. 
It always returns the same value until you change the constant in sources and build a new version of the application.

2. Emulating high CPU load operation

Executing service with any other then EUR/USD values, e.g. PLN/EUR, causes special __rate wizzard__ feature will be used. The feature is implemented 
in `rateWizzard` method of `CurrencyProviderRest` class. 
The `rateWizzard` method uses same mathematics magic that consumes a lot of CPU ;-). 
Its CPU load and latency depends on value of environment variable `WIZARD_LOOPS` passed to the application. The variable `WIZARD_LOOPS` takes numbers as values. Setting it to `10000000` results approximately 1 second latency (running on Lenovo IdeaPad 710s laptop with i7 7500U CPU).

There is also parameter set by environment variable `SPREAD` that causes the rate is multiplied by 1 + its value. 
So when the variable `SPREAD`is set to 0.003 it will result the rate for EUR/USD is 1.003 instead of 1.0.

The application uses spring boot actuator /health endpoint to provide health checks. 

###### Usage: 
`http:<host>:<port>/rate?source=A&destination=B` gets rate for currency A/B, e.g. get the rate of currency pair 
EUR/USD by executing `http://localhost:8080/rate?source=EUR&destination=USD`
###### Build:
Command `mvn clean package` creates jar file `target/k8s-demo-currency-provider-0.0.1-SNAPSHOT.jar`.
Command `docker build -t k8s-demo-currency-provider:0.0.1 .`  builds docker image `k8s-demo-currency-provider` and tag it with version `0.0.1`.
###### Deployment:
```
kubectl create -f k8s/k8s-demo-currency.configmap.yaml
kubectl create -f k8s/k8s-demo-currency-provider.deployment.yaml
kubectl create -f k8s/k8s-demo-currency-provider.service.yaml
```
You can obtain service url running
```
export DEMO_IP=$(kubectl get nodes -o jsonpath="{.items[0].status.addresses[0].address}")
export DEMO_PORT=$(kubectl get services k8s-demo-currency-provider -o jsonpath="{.spec.ports[0].nodePort}")
echo "$DEMO_IP:$DEMO_PORT"
```
# Demonstration scenario
## Prerequirements
1. Before you begin ensure that you have installed on your PC:
  * Java SDK 1.8
  * maven
  * Docker
  * kubectl
  * [minikube](https://github.com/kubernetes/minikube)

2. Configure minikube - add heapster addon and connect its docker demon:
```
minikube addons enable heapster
minikube start
eval $(minikube docker-env)
```
3. Build the application and the docker image:
```
mvn clean package
docker build -t k8s-demo-currency-provider:0.0.1 .
```
You don't have to push docker image to a registry thanks to using `eval $(minikube docker-env)` 
and imagePullPolicy set to IfNotPresent in all examples below.

###### Notice 
If the demonstration is on a real kubernetes cluster connected to private registry you should modify command above and run
```
docker build -t registry.example.com/k8s-demo-currency-provider:0.0.1 .
```
change `registry.example.com` to your registry address. You should also modify kubernetes yaml descriptors in 
k8s directory adding registry prefix in image property of deployments. Also every command operating on image below should be customized. You should also execute `docker push registry.example.com/k8s-demo-currency-provider:0.0.1` after `docker build -t registry.example.com/k8s-demo-currency-provider:0.0.1 .` command (for application version 0.0.1 as well as 0.0.2 changing registry.example.com/k8s-demo-currency-provider:0.0.1 to registry.example.com/k8s-demo-currency-provider:0.0.2).

## Deployment of sample spring boot application
This is demonstration of most basic kubernetes features. Deploy the application on your kubernetes. 
Tell kubernetes to do this by creating ConfigMap with application configuration and deployment:
```
kubectl create -f k8s/k8s-demo-currency.configmap.yaml
kubectl create -f k8s/k8s-demo-currency-provider.deployment.yaml
``` 
Kubernetes allocates all necessary resources for the application and then creates a pod with the application as described 
in deployment and uses configuration provided in the ConfigMap.
You can see what happens on the kubernetes dashboard in a browser by executing `minikube dashboard` or in terminal `kubectl get pods`. 
You should find newly created, running pod. You can check its logs in the dashboard or by executing `kubectl logs <pod aname>`
You can also see CPU and memory loads of a pod on the dashboard.
Application configuration is stored in ConfigMap k8s-demo-currency. You can check its content in the dashboard or by executing `kubectl get configmap -o yaml`.
Execute `kubectl exec <pod name> env` and find the SPREAD environment variable.
Change `spread` value in the k8s-demo-currency ConfigMap in the dahsboard.
Again execute `kubectl exec <pod name> env` and find the SPREAD environment variable.
Notice that when you change values in a ConfigMap, the deployment using them will not be notified automatically. 
You have to manually force restart of the pods managed by deployment. 
You can do it by deleting pods - kubernetes will create new instances with fresh configuration loaded from changed ConfigMap. Run `kubectl delete <pod aname>` or go inside pod by executing `kubectl exec -it <pod aname> /bin/sh` and than from the shell of the container kill the java process.
In the real world to avoid manual restarts you can use tools like helm with charts or consider using immutable ConfigMaps (do not modify ConfigMap when you need to change its value, create a new one and repleace usage of the old one to the newly created). 
You can not access the application http endpoint. You must create a kubernetes service what is subject of next section.

## Expose deployment on kubernetes service
When the pods where created, they are not accessible from outside of the cluster. 
They are not even accessible for other pods in the same namespace of kubernetes.
You have to create a service to expose pods ports. It will also provide load balancing of network traffic to our application. 
By choosing the service type of NodePort you can access it from outside of the cluster.
Below command creates service of type NodePort on kubernetes for `k8s-demo-currency-provider` pods: 
```
kubectl create -f k8s/k8s-demo-currency-provider.service.yaml
``` 
It exposes pod's port 8080 on random generated port for outside access. 
You can launch a browser with url of created service by executing:
```
minikube service k8s-demo-currency-provider
```
It will launch browser with correct url containing ip host of minikube and port assigned by kubernetes to `k8s-demo-currency-provider` service.
Add suffix `/rate?source=EUR&destination=USD` in the url in browser and ensure that application is running and its response is correct.
You can also obtain external service url as described in __Deployment__ section above.
```
export DEMO_IP=$(kubectl get nodes -o jsonpath="{.items[0].status.addresses[0].address}")
export DEMO_PORT=$(kubectl get services k8s-demo-currency-provider -o jsonpath="{.spec.ports[0].nodePort}")
curl "$DEMO_IP:$DEMO_PORT/rate?source=EUR&destination=USD"
```
You can replay test of changing configuration from previous scenario and check application response after changed configuration and then pod restarted. 

## Scale the application
#### Availlability and response test
Test described below will be used also in further sections for demonstration various features of kubernetes.
In the new terminal execute below script. 
```
export DEMO_IP=$(kubectl get nodes -o jsonpath="{.items[0].status.addresses[0].address}")
export DEMO_PORT=$(kubectl get services k8s-demo-currency-provider -o jsonpath="{.spec.ports[0].nodePort}")
while true; do sleep 1; curl --max-time 1 "$DEMO_IP:$DEMO_PORT/rate?source=EUR&destination=USD"; echo -e ' -> '$(date); done
```
It will call our service and prints ones a second result in the terminal. 
Script checks whether application is available by printing content of the rate service.
The curl command waits only 1 second for an answer. If it will not receive an answer after 1 second it prints an error message.
#### Scale
Run `kubectl scale deployment k8s-demo-currency-provider --replicas 2`.
Look at dashboard or run `kubectl get pods`. Kubernetes creates second pod containing our application in container grabbed from image `k8s-demo-currency-provider:0.0.1`.
Check deployment status by `kubectl get deployment` and `kubectl rollout status deployment/k8s-demo-currency-provider`.
Check what happened in the second terminal - application could print error messages. We will explain it later.

## Self-healing
Run the __availability and response test__ described in __scale the application__ above.
Delete the k8s-demo-currency-provider pod on dashboard or by kubectl.
(Run `kubectl get pods` and then `kubectl delete pod <pod name>` where `<podn ame>` you get from output of the first command).
Kubernetes immediate creates a new pod. You can check it out in the dashboard or by executing `kubectl get pods`.
Look at output of the second terminal - application could print error messages. 
Self-healing works, but kubernetes need to now when application finished initialization and whether is up and running. 
Next demo improve this (and previous test as well).

## readinessProbe & livenessProbe
There are two mechanisms introduced in kubernetes with purpose of health check. 
First one is readinessProbe which checks whether application initialization process has finished. 
Only when readinessProbe of a pod succeeds kubernetes will start to serve network traffic to the pod. 
Kubernetes uses livenessProbe in a pod to check if application is sill running. 
To enable lifnessProbe and readynessProbe in deployment edit deployment. 
Edit deployment descriptor - run `kubectl edit deployment k8s-demo-currency-provider` and then in an editor put below text in the container section
```
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 40
          periodSeconds: 1
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 1
```
You can run `kubectl apply -f k8s/k8s-demo-currency-provider.healthcheck.yaml` instead of editing descriptor. 
The file contains already set livenessProbe and readinessProbe values.

Run the __availability and response test__ described in __scale the application__ above.
Delete pod and check output of the __new terminal window__. 
Notice that there are no errors in the output. 
Self-healing mechanism working together with readinessProbe fixed errors that were seen in the previous tests.
You can also run again test from __Scacle__ and ensure that there are no errors as well.

## Zero downtime deployment
We will prepare a new version of our application to demonstrate zero downtime deployment.
Edit sources and change value of `eurUsdRate` attribute in the class `CurrencyProviderRest`. 
Set it to 2.0 or something that will differ from current value. 
We will print the value with spread in a loop like in previous tests, so we need to notice when application version was changed. 
Build new version of application executing `mvn clean package`. 
It will create new file `target/k8s-demo-currency-provider-0.0.1-SNAPSHOT.jar` with modified rate service.
Then run `docker build -t k8s-demo-currency-provider:0.0.2 .`. It builds a new docker image `k8s-demo-currency-provider` and tag it with the new version `0.0.2`.
You can check that there is a new image with a changed image id in your local docker registry running `docker images | grep k8s-demo`.
Ensure that you have property __replicas__ set to at least 2 in deployment `k8s-demo-currency-provider`. 
If not run `kubectl scale deployment k8s-demo-currency-provider --replicas 2`.
Run the __availability and response test__ described in __scale the application__ above.
Run `kubectl set image deployment/k8s-demo-currency-provider k8s-demo-currency-provider=k8s-demo-currency-provider:0.0.2` 
Kubernetes starts rolling update of our application changing its image version from 0.0.1 to 0.0.2 one by one. 
It respects rules provided by readinessProbe, so our application is available without any breaks. 
Look at the output on the new terminal. Application was printing in a loop value 1.0 and then after deployment changed value to 2.0. 
There were no errors in the output. We deployed the new version of application without any downtime.

## Configmaps and secrets 
Secrets are similar to ConfigMaps. Main difference is in encoding. 
ConfigMaps are not encoded. Secret uses base64 for encoding and its purpose is to store classified data.
We will demonstrate it by using equivalent secret in place of existing ConfigMap.
Run `kubectl create -f k8s-demo-currency-provider.secret.yaml`. It creates secret containing the same data as in existing ConfigMap.  
You can find created secret in the dashboard and compare it to the existing ConfigMap.
Now modify deployment. Run `kubectl edit deployment k8s-demo-currency-provider`.
In the editor you should find `configMapKeyRef` text and change it to `secretKeyRef`. 
Deployment will use value from secret in place of ConfigMap.
Bring back configuration based on ConfigMap for further use.

## Simple resources utilization show on dashboard
Change `wizardLoads` value in ConfigMap - set it value to 100000000 (expected ~10s latency per call).
Pods will not use new configuration automatically. You have to manually restart pods by deleting it in the dashboard or by kubectl.
Execute the `/rate` endpoint with __rateWizzard__ (eg. PLN/USD instead of EUR/USD) in the browser or using curl. 
Script below runs it in a loop 5 times.
```
export DEMO_IP=$(kubectl get nodes -o jsonpath="{.items[0].status.addresses[0].address}")
export DEMO_PORT=$(kubectl get services k8s-demo-currency-provider -o jsonpath="{.spec.ports[0].nodePort}")
for ((i=1;i<=5;i++)); do sleep $((5 + $i)); curl "$DEMO_IP:$DEMO_PORT/rate?source=PLN&destination=USD"; echo -e $(date); done
```
Check CPU and memory loads in the dashboard.
Wait a minute and run again the `/rate` with __rateWizzard__ endpoint. 
Look at dashboard and CPU and memory diagrams.

## Horizontal pod autoscaling - HPA
HPA needs resources to be configured in the deployment descriptor for running. 
Edit k8s-demo-currency-provider deployment and add resources configuration.
Run `kubectl edit deployment k8s-demo-currency-provider` and in the editor place below text in container section:
```
        resources:
          limits:
            cpu: 500m
            memory: 300Mi
          requests:
            cpu: 250m
            memory: 200Mi
```
File `k8s/k8s-demo-currency-provider.health.resources.yaml` already contains above modifications. 
So you can use `kubectl apply -f k8s/k8s-demo-currency-provider.health.resources.yaml` instead.
Run `kubectl autoscale deployment k8s-demo-currency-provider --min=2 --max=5 --cpu-percent=80`. 
It enables HPA for our deployment with constraints for count of pods. Minimum is set to 2 and maximum to 5. 
HPA uses CPU statistic provided by heapser. The parameter --cpu-percent tells HPA when it should scale the application horizontally.
You can check status of HPA executing `kubectl get hpa` and `kubectl describe hpa`. 
We need to cause high CPU load for demonstration of HPA. 
We will use the rate service with __rateWizzard__ of our application.
Run a few times in a short delays the `/rate` endpoint with currencies different when EUR/USD eg. PLN/USD.
```
curl "$DEMO_IP:$DEMO_PORT/rate?source=PLN&destination=USD"
```
Now you can check how HPA works by executing command `kubectl describe hpa`.
The __rateWizzard__  should increase the CPU load. 
Notice that there is delay in gathering CPU statistics introduced by heapster and HPA itself. 
So wait a minute until HPA gathers statistics and start works.
 
HPA reads heapster statistics and calculates desired pods count.
It cares about pods count adequate to CPU load, resources configuration in deployment and HPA parameters. 
Here is part of output of command `kubectl describe hpa` that contains logs of 15 minutes working HPA:
```
  FirstSeen    LastSeen   Count  From            SubObjectPath  Type      Reason       Message
  ---------    --------   -----  ----            -------------  --------   ------       -------
  14m     13m       3  {horizontal-pod-autoscaler }         Normal    MetricsNotAvailableYet unable to get metrics for resource cpu: no metrics returned from heapster
  13m     12m       3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 1 (avgCPUutil: 22, current replicas: 2)
  12m     11m       3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 1 (avgCPUutil: 5, current replicas: 2)
  10m     10m       1  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 3 (avgCPUutil: 94, current replicas: 2)
  10m     10m       1  {horizontal-pod-autoscaler }         Normal    SuccessfulRescale  New size: 3; reason: CPU utilization above target
  7m      6m    3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 4 (avgCPUutil: 102, current replicas: 3)
  6m      6m    2  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 5 (avgCPUutil: 131, current replicas: 3)
  6m      6m    1  {horizontal-pod-autoscaler }         Normal    SuccessfulRescale  New size: 5; reason: CPU utilization above target
  4m      3m    3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 4 (avgCPUutil: 51, current replicas: 5)
  3m      2m    3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 3 (avgCPUutil: 43, current replicas: 5)
  2m      1m    3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 3 (avgCPUutil: 44, current replicas: 5)
  1m      41s       3  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 1 (avgCPUutil: 16, current replicas: 5)
  11s     11s       1  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    (events with common reason combined)
  11s     11s       1  {horizontal-pod-autoscaler }         Normal    SuccessfulRescale  New size: 2; reason: All metrics below target
  11s     11s       2  {horizontal-pod-autoscaler }         Normal    DesiredReplicasComputed    Computed the desired num of replicas: 1 (avgCPUutil: 4, current replicas: 5)
```
As you can see HPA created 3-rd pod when it noticed that CPU load was to high. 
After a few minutes, when CPU load decreased, HPA scaled it down to minimum count of 2 pods.
If during your test HPA did not created a new pod, probably you should increase CPU loads by running additional `/rate` with __rateWizzard__  endpoint.
You can also check ConfigMap and set __wizardLoops__ to a higher value.
