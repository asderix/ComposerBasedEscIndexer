from airflow import DAG
from airflow.decorators import task
from airflow.utils.dates import days_ago
from google.cloud import storage, bigquery
from airflow.providers.apache.beam.operators.beam import BeamRunPythonPipelineOperator
from airflow.providers.apache.beam.hooks.beam import BeamRunnerType
import requests
import os

default_args = {
    'owner': 'airflow',
    'retries': 3,
}

# Change this settings if you have different ones:
BUCKET_NAME = 'sanctionlist'
GCS_OBJECT_NAME = 'temp_sanction_file.csv'
BIGQUERY_DATASET = 'escindex'
BIGQUERY_TABLE = 'sanctionlist'

with DAG(
    dag_id='sanctionlist_indexing',
    default_args=default_args,
    start_date=days_ago(1),
    schedule_interval='@daily',
    catchup=False,
) as dag:

    @task
    def download_file():
        url = 'https://data.opensanctions.org/datasets/20241022/ch_seco_sanctions/targets.simple.csv?v=20241022120902-oux'
        response = requests.get(url)
        response.raise_for_status()
        # Define your temp path for the file download:
        file_path = '/tmp/temp_sanction_file.csv'
        with open(file_path, 'wb') as f:
            f.write(response.content)
        return file_path

    @task
    def upload_to_gcs(file_path: str):        
        storage_client = storage.Client(project='<YOUR-PROJECT-ID>')
        bucket = storage_client.bucket(BUCKET_NAME)
        blob = bucket.blob(GCS_OBJECT_NAME)
        blob.upload_from_filename(file_path)
        return GCS_OBJECT_NAME

    @task
    def load_to_bigquery(gcs_object_name: str):
        client = bigquery.Client(project='<YOUR-PROJECT-ID>')
        table_id = f"{BIGQUERY_DATASET}.{BIGQUERY_TABLE}"
        
        job_config = bigquery.LoadJobConfig(            
            source_format=bigquery.SourceFormat.CSV,
            skip_leading_rows=1,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            autodetect=True
        )
        
        uri = f"gs://{BUCKET_NAME}/{gcs_object_name}"
        load_job = client.load_table_from_uri(uri, table_id, job_config=job_config)
        
        load_job.result()      

    dataflow_indexer = BeamRunPythonPipelineOperator(
        runner=BeamRunnerType.DataflowRunner,
        task_id="dataflow_indexer",
        py_file="<PATH-TO-YOUR-PIPELINE-CODE>/index_pipeline.py",
        py_options=[],        
        py_requirements=["apache-beam[gcp]==2.47.0"],
        py_interpreter="python3",
        py_system_site_packages=False,        
    )

    file_path = download_file()
    gcs_object_name = upload_to_gcs(file_path)
    load_to_bigquery(gcs_object_name) >> dataflow_indexer

