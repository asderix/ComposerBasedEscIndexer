import apache_beam as beam
from apache_beam.options.pipeline_options import PipelineOptions, GoogleCloudOptions
from google.cloud import bigquery
import json
import requests
import logging

# Configure your log strategy. Just a local example:
logging.basicConfig(
    filename="/var/log/pipeline.log",
    filemode="a",
    format="%(asctime)s - %(levelname)s - %(message)s",
    level=logging.INFO,
)


class BigQueryToJsonTransform(beam.DoFn):
    def process(self, record):
        logging.info(f"BigQueryToJsonTransofrm: {record}")        
        return [json.dumps(record)]


class SendToRestAPI(beam.DoFn):
    def __init__(self, api_url):
        self.api_url = api_url

    def process(self, records_batch):
        logging.info(f"Sending batch to API: {records_batch}")
        headers = {"Content-Type": "application/json"}
        response = requests.post(self.api_url, data=records_batch, headers=headers)
        if response.status_code == 200:
            return [f"Success: {response.status_code}"]
        else:
            return [f"Failed: {response.status_code}, {response.text}"]


def run(argv=None):    
    pipeline_options = PipelineOptions()
    google_cloud_options = pipeline_options.view_as(GoogleCloudOptions)
    google_cloud_options.project = "<YOUR-PROJECT-ID>"
    google_cloud_options.temp_location = "gs://<YOUR-BUCKET>/temp"
    google_cloud_options.staging_location = "gs://<YOUR-BUCKET>/staging"

    logging.info(f"Start Pipeline: {pipeline_options}")

    query = "SELECT id, name, birth_date AS dob FROM `<YOUR-PROJET-ID>.escindex.sanctionlist` WHERE schema = 'Person' LIMIT 105"

    with beam.Pipeline(options=pipeline_options) as p:
        (
            p
            | "ReadFromBigQuery"
            >> beam.io.Read(
                beam.io.ReadFromBigQuery(query=query, use_standard_sql=True)
            )
            | "BatchInto100" >> beam.BatchElements(min_batch_size=1, max_batch_size=100)
            | "ConvertToJSON" >> beam.ParDo(BigQueryToJsonTransform())
            | "SendToAPI"
            >> beam.ParDo(SendToRestAPI(api_url="<YOUR-HOST>:<PORT>/person-batch"))
        )


if __name__ == "__main__":
    run()
