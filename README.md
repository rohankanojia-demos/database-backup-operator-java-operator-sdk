# database-backup-operator

This repository implements a simple controller for performing regular database backups by watching a CustomResourceDefinition (CRD) named Backup.

User would provide a custom resource named Backup with following parameters:
```yaml
apiVersion: org.linuxfoundation.demos/v1alpha1
kind: Backup
metadata:
  name: my-database-backup
spec:
  database:
    name: postgres # Database Name
    host: postgres.default.svc.cluster.local # Database Host
    port: 5432 # Database Port
    user: postgres #Database user
    passwordSecret: postgres  # Reference to a Kubernetes Secret
  schedule: "0 15 10 * * ?" # Schedule for taking backup
  retention: 7  # Keep backups for 7 days
  storageLocation: /mnt/backups  # Local or NFS storage location
```

It demonstrates how to perform basic operations such as:

- How to register a reconciler for new custom resource (custom resource type) of type Backup using `io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration` annotation.
- How to create/get/list instances of your new resource type Foo.

## Prerequisites

Since this operator takes backup of a PostegreSQL database, you need to install a database on your Kubernetes Cluster. For this demo, you can use the example in resources folder:
```shell
kubectl create -f src/main/resources/postgres-db.yaml
```

## How to Run?

```shell
# Install Custom Resource Definition
kubectl create -f src/main/resources/crd/database-backup-crd.yaml
# Install ClusterRole, ClusterRoleBinding and ServiceAccount for Operator to work with
kubectl create -f src/main/resources/backup-serviceaccount-and-role-binding.yml
# Deploy Operator to Kubernetes cluster using Kubernetes Maven Plugin
# (Optional) To point your shell to minikube's docker-daemon, run:
eval $(minikube -p minikube docker-env)
mvn package k8s:build k8s:resource k8s:apply
```
Once Operator has been deployed to Cluster, check for pods (there should be one named `database-backup-operator` running:
```shell
kubectl get pods
```

Create an instance of `Foo` resource:
```shell
kubectl create -f src/main/resources/example-db-backup.yaml
backup.org.linuxfoundation.demos/my-database-backup created
```

The operator also provides view of backed up files on its homepage. Check application url and open it in browser:
```shell
# Get application URL and open in browser
minikube service database-backup-operator list --url
```

When you'll open the application in browser, you would see that it's displaying backed up files (depending on the schedule).
