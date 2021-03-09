# pulsar-inspector-connector

## Installing Pulsar k8s cluster

On Linux, Microk8s can be used. Minikube is another popular option

### Installing Microk8s on Ubuntu 20.10

```
sudo snap install --classic microk8s
sudo usermod -a -G microk8s $USER

# logout & login to make microk8s group effective

# this was required to get microk8s traffic to work, might be requires after every reboot
sudo iptables -P FORWARD ACCEPT

# install microk8s addons
microk8s enable host-access
microk8s enable dns
microk8s enable ingress
microk8s enable storage
microk8s enable registry

# kubectl
sudo snap install --classic kubectl
microk8s config -l > $HOME/.kube/config
chmod 0600 $HOME/.kube/config
# should work now
kubectl get --all-namespaces all

# Lens
sudo snap install --classic kontena-lens
kontena-lens&
```


### Installing charts and values.yaml for Minikube / single node

```
helm repo add apache https://pulsar.apache.org/charts
helm update
curl https://raw.githubusercontent.com/apache/pulsar-helm-chart/master/examples/values-minikube.yaml > values.yaml
```

### Installing Pulsar
```
helm upgrade --install --namespace pulsar --create-namespace -f values.yaml --set initialize=true pulsar apache/pulsar 
```


## Installing pulsar-inspector-connector

Pre-requisite: pulsar-admin


```
# package the connector
mvn package

# copy to the pulsar broker in the k8s cluster
kubectl cp --namespace=pulsar target/pulsar-inspector-connector-1.0-SNAPSHOT.nar pulsar-broker-0:/pulsar/connectors/

PULSAR_ADMIN_URL=http://$(kubectl get --namespace=pulsar service/pulsar-proxy --template '{{ .spec.clusterIP }}')

pulsar-admin --admin-url $PULSAR_ADMIN_URL sources reload

pulsar-admin --admin-url $PULSAR_ADMIN_URL sources available-sources

pulsar-admin --admin-url $PULSAR_ADMIN_URL sources create \
 --name test-pulsar-inspector \
 --destination-topic-name test-pulsar-inspector_sink_topic \
 --source-type pulsar-inspector
```

## Verifying classloader information

Pre-requisite: pulsar-client on the PATH

```
PULSAR_URL=pulsar://$(kubectl get --namespace=pulsar service/pulsar-proxy --template '{{ .spec.clusterIP }}'):6650

pulsar-client --url $PULSAR_URL consume -s test-subscription -n 1 test-pulsar-inspector_sink_topic | perl -0777 -p -e 's/.*content:(\{.*\}).*/$1/s' > classloader_report.json

cat classloader_report.json|jq -r .classloader_report

kubectl --namespace=pulsar exec -it pf-public-default-test-pulsar-inspector-0 -- jar tvf /pulsar/instances/java-instance.jar  |grep pom.xml

kubectl --namespace=pulsar exec -it pf-public-default-test-pulsar-inspector-0 -- bash

cat /proc/1/cmdline |xargs -0 echo
```
