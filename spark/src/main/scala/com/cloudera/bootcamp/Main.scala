package com.cloudera.bootcamp

import org.apache.spark._
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.hive._
import org.apache.spark.sql.functions._

/**
  * Created by hdulay on 6/28/17.
  */
object Main extends App {

  val conf = new SparkConf().setAppName("bootcamp").setMaster("yarn")
  val spark = new SparkContext(conf)


  val sqlContext = new HiveContext(spark)
  import sqlContext.implicits._


  val galaxies = sqlContext.read.format("jdbc").options(Map(
    "url" -> "jdbc:oracle:thin:@gravity.cghfmcr8k3ia.us-west-2.rds.amazonaws.com:15210:gravity",
    "dbtable" -> "gravity.galaxies",
    "user" -> "gravity",
    "password"->"bootcamp",
    "driver" -> "oracle.jdbc.driver.OracleDriver")).load()

  val detectors = sqlContext.read.format("jdbc").options(Map(
    "url" -> "jdbc:oracle:thin:@gravity.cghfmcr8k3ia.us-west-2.rds.amazonaws.com:15210:gravity",
    "dbtable" -> "gravity.detectors",
    "user" -> "gravity",
    "password"->"bootcamp",
    "driver" -> "oracle.jdbc.driver.OracleDriver")).load()

  val astrophysicists = sqlContext.read.format("jdbc").options(Map(
    "url" -> "jdbc:oracle:thin:@gravity.cghfmcr8k3ia.us-west-2.rds.amazonaws.com:15210:gravity",
    "dbtable" -> "gravity.astrophysicists",
    "user" -> "gravity",
    "password"->"bootcamp",
    "driver" -> "oracle.jdbc.driver.OracleDriver")).load()

  val measurements = sqlContext.sql("SELECT * FROM gravity.measure_sm") // hive, sqooped in
  measurements.cache()

  sqlContext.read.parquet("/user/hive/warehouse/gravity/measure_sm")


//  val rdd = measurements.map(row => {
//    // where cast(amplitude_1 as decimal(20,16)) > 0.995 and cast(amplitude_3 as decimal(20,16)) > 0.995 and cast(amplitude_2 as decimal(20,16)) < 0.005
//    val flag = row.getString(5).toDouble > 0.995 && row.getString(7).toDouble > 0.995 && row.getString(6).toDouble < 0.005
//    Row.fromSeq(row.toSeq :+ flag)
//  })

  val flagged = measurements.withColumn("flag", measurements("amplitude_1") > 0.995 && measurements("amplitude_3") > 0.995 && measurements("amplitude_2") < 0.005)
  val joined = flagged.
    join(org.apache.spark.sql.functions.broadcast(galaxies), Seq("galaxy_id")).
    join(org.apache.spark.sql.functions.broadcast(detectors), Seq("detector_id")).
    join(org.apache.spark.sql.functions.broadcast(astrophysicists), Seq("astrophysicist_id"))

  // change the schema for the joined dataframe so that same column anmes
  // do not conflict
  var i = 0
  val schema2 = joined.schema.map(field => {
    i += 1
    StructField(field.name + (i), field.dataType, field.nullable)
  })
  val flagged2 = sqlContext.createDataFrame(joined.map(r => r), StructType(schema2))

  galaxies.write.option("compression","snappy").mode("overwrite").saveAsTable("gravity2.galaxies")
  detectors.write.option("compression","snappy").mode("overwrite").saveAsTable("gravity2.detectors")
  astrophysicists.write.option("compression","snappy").mode("overwrite").saveAsTable("gravity2.astrophysicists")
  flagged2.write.option("compression","snappy").mode("overwrite").saveAsTable("gravity2.measurements_joined")

  spark.stop
}
