/**
 * Copyright (C) 2017 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.managedeposit

import java.net.{ URI, URL }

import better.files.File
import better.files.File.root
import com.yourmediashelf.fedora.client.FedoraCredentials
import nl.knaw.dans.easy.managedeposit.properties.{ DepositPropertiesRepository, FileDepositPropertiesRepository }

import scala.collection.JavaConverters._
import nl.knaw.dans.lib.string._
import org.apache.commons.configuration.PropertiesConfiguration

case class Configuration(version: String,
                         sword2DepositsDir: File,
                         ingestFlowInbox: File,
                         ingestFlowInboxArchived: Option[File],
                         fedoraCredentials: FedoraCredentials,
                         landingPageBaseUrl: URI,
                         dansDoiPrefixes: List[String],
                        ) {
  def depositPropertiesFactory: DepositPropertiesRepository = {
    implicit val prefixes: List[String] = dansDoiPrefixes
    new FileDepositPropertiesRepository(sword2DepositsDir, ingestFlowInbox, ingestFlowInboxArchived)
  }
}

object Configuration {

  def apply(home: File): Configuration = {
    val cfgPath = Seq(
      root / "etc" / "opt" / "dans.knaw.nl" / "easy-manage-deposit",
      home / "cfg")
      .find(_.exists)
      .getOrElse { throw new IllegalStateException("No configuration directory found") }
    val properties = new PropertiesConfiguration() {
      setDelimiterParsingDisabled(true)
      load((cfgPath / "application.properties").toJava)
    }

    new Configuration(
      version = (home / "bin" / "version").contentAsString.stripLineEnd,
      sword2DepositsDir = File(properties.getString("easy-sword2")),
      ingestFlowInbox = File(properties.getString("easy-ingest-flow-inbox")),
      ingestFlowInboxArchived = properties.getString("easy-ingest-flow-inbox-archived").toOption.map(File(_)).filter(_.exists),
      fedoraCredentials = new FedoraCredentials(
        new URL(properties.getString("fedora.url")),
        properties.getString("fedora.user"),
        properties.getString("fedora.password"),
      ),
      landingPageBaseUrl = new URI(properties.getString("landing-pages.base-url")),
      dansDoiPrefixes = properties.getList("dans-doi.prefixes").asScala.toList.map(_.asInstanceOf[String]),
    )
  }
}