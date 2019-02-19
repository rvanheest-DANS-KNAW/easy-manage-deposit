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

import java.io.{ ByteArrayOutputStream, PrintStream }
import java.text.SimpleDateFormat
import java.util.{ Calendar, UUID }

import nl.knaw.dans.easy.managedeposit.State._
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Inspectors, Matchers }

import scala.util.matching.Regex

class ReportGeneratorSpec extends TestSupportFixture
  with Matchers
  with MockFactory
  with Inspectors {

  "filterDepositsByDepositor" should "only return deposits where the id of the depositor matches" in {
    val deposits = List(
      createDeposit("dans-1", DRAFT),
      createDeposit("dans-1", SUBMITTED),
      createDeposit("dans-3", SUBMITTED),
    )
    ReportGenerator.filterDepositsByDepositor(deposits, Some("dans-1")).size shouldBe 2
  }

  it should "should return empty list if none of the id's matches" in {
    val deposits = List(
      createDeposit("dans-2", DRAFT),
      createDeposit("dans-5", SUBMITTED),
      createDeposit("dans-3", SUBMITTED),
    )
    ReportGenerator.filterDepositsByDepositor(deposits, Some("dans-1")) shouldBe empty
  }

  it should "should return all deposits if the given depositorId is empty" in {
    val deposits = List(
      createDeposit("dans-2", DRAFT),
      createDeposit("dans-5", SUBMITTED),
      createDeposit("dans-3", SUBMITTED),
    )
    ReportGenerator.filterDepositsByDepositor(deposits, None).size shouldBe 3
  }

  it should "should return all deposits if the given depositorId is empty and one of the depositorIds is null" in {
    val deposits = List(
      createDeposit(null, DRAFT),
      createDeposit("dans-5", SUBMITTED),
      createDeposit("dans-3", SUBMITTED),
    )
    ReportGenerator.filterDepositsByDepositor(deposits, None).size shouldBe 3
  }

  it should "should skip all depositorId = null deposits if the given depositorId is given" in {
    val deposits = List(
      createDeposit(null, DRAFT),
      createDeposit("dans-1", SUBMITTED),
      createDeposit("dans-1", SUBMITTED),
    )
    ReportGenerator.filterDepositsByDepositor(deposits, Some("dans-1")).size shouldBe 2
  }

  "groupDepositsByState" should "return a map with all deposits sorted By their state and null should be mapped to UNKNOWN" in {
    val deposits: List[Deposit] = createDeposits
    val mappedByState = ReportGenerator.groupAndSortDepositsAlphabeticallyByState(deposits).toMap
    mappedByState.getOrElse(ARCHIVED, Seq()).size shouldBe 2
    mappedByState.getOrElse(DRAFT, Seq()).size shouldBe 1
    mappedByState.getOrElse(FINALIZING, Seq()).size shouldBe 1
    mappedByState.getOrElse(INVALID, Seq()).size shouldBe 1
    mappedByState.getOrElse(REJECTED, Seq()).size shouldBe 1
    mappedByState.getOrElse(STALLED, Seq()).size shouldBe 1
    mappedByState.getOrElse(SUBMITTED, Seq()).size shouldBe 4
    mappedByState.getOrElse(UNKNOWN, Seq()).size shouldBe 4 // 2 + 2 null values
  }

  "output Summary" should "should contain all deposits" in {
    if (!testDir.exists) { //output of rapport is written to target
      testDir.createDirectories()
    }

    val deposits = createDeposits
    val now = Calendar.getInstance().getTime
    val format = new SimpleDateFormat("yyyy-MM-dd")
    val currentTime = format.format(now)
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true)
    try {
      ReportGenerator.outputSummary(deposits, Some("dans-1"))(ps)
    } finally {
      ps.close()
    }

    val reportOutput = baos.toString
    reportOutput should include(s"Timestamp          : $currentTime")
    reportOutput should include(f"Number of deposits : ${ 15 }%10d")
    reportOutput should include("Total space        :      1.8 M") // (129000 * 15 ) / (1024 * 1024)
    reportOutput should include regex toStateDetailsRegex(ARCHIVED, 2, 252.0)
    reportOutput should include regex toStateDetailsRegex(DRAFT, 1, 126.0)
    reportOutput should include regex toStateDetailsRegex(FINALIZING, 1, 126.0)
    reportOutput should include regex toStateDetailsRegex(INVALID, 1, 126.0)
    reportOutput should include regex toStateDetailsRegex(REJECTED, 1, 126.0)
    reportOutput should include regex toStateDetailsRegex(STALLED, 1, 126.0)
    reportOutput should include regex toStateDetailsRegex(SUBMITTED, 4, 503.9)
    reportOutput should include regex toStateDetailsRegex(UNKNOWN, 4, 503.9)
  }

  "outputErrorReport" should "only print the deposits containing an error" in {
    val baos = new ByteArrayOutputStream()
    val errorDeposit = createDeposit("dans-0", ARCHIVED).copy(dansDoiRegistered = Some(false)) //violates the rule ARCHIVED must be registered
    val ps: PrintStream = new PrintStream(baos, true)
    val deposits = List(
      errorDeposit,
      createDeposit("dans-1", SUBMITTED), //does not violate any rule
      createDeposit("dans-2", SUBMITTED), //does not violate any rule
    )
    outputErrorReportManaged(ps, deposits)
    val errorReport = baos.toString
    errorReport should include(createCsvRow(errorDeposit)) // only the first deposit should be added to the report
  }

  it should "not print any csv rows if no deposits violate the rules" in {
    val baos = new ByteArrayOutputStream()
    val ps: PrintStream = new PrintStream(baos, true)
    val deposits = List(
      createDeposit("dans-0", DRAFT).copy(dansDoiRegistered = Some(false)),
      createDeposit("dans-1", SUBMITTED),
      createDeposit("dans-1", SUBMITTED),
    )
    outputErrorReportManaged(ps, deposits)

    val errorReport = baos.toString
    deposits.foreach(deposit => errorReport should not include createCsvRow(deposit)) // None of the deposits should be added to the report
  }

  it should "print any deposit that has one of the states null, UNKNOWN, INVALID, FAILED, REJECTED or ARCHIVED + not-registered" in {
    val baos = new ByteArrayOutputStream()
    val ps: PrintStream = new PrintStream(baos, true)
    val deposits = List(
      createDeposit("dans-0", ARCHIVED).copy(dansDoiRegistered = Some(false)), //violates the rule ARCHIVED must be registered
      createDeposit("dans-1", FAILED),
      createDeposit("dans-2", REJECTED),
      createDeposit("dans-3", INVALID),
      createDeposit("dans-4", UNKNOWN),
      createDeposit("dans-5", null),
    )
    outputErrorReportManaged(ps, deposits)

    val errorReport = baos.toString
    forEvery(deposits)(deposit => errorReport should include(createCsvRow(deposit))) //all deposits should be added to the report
  }

  private def outputErrorReportManaged(ps: PrintStream, deposits: List[Deposit]): Unit = {
    try {
      ReportGenerator.outputErrorReport(deposits)(ps)
    } finally {
      ps.close()
    }
  }

  private def toStateDetailsRegex(state: State, amount: Int, size: Double): Regex = s"$state.+$amount.+$size".r

  private def createCsvRow(deposit: Deposit): String = {
    s"${ deposit.depositor }," +
      s"${ deposit.depositId }," +
      s"${ Option(deposit.state).getOrElse("") }," +
      s"${ deposit.dansDoiIdentifier }," +
      s"${ deposit.registeredString }," +
      s"${ deposit.fedoraIdentifier.toString }," +
      s"${ deposit.creationTimestamp }," +
      s"${ deposit.lastModified }," +
      s"${ deposit.description }," +
      s"${ deposit.numberOfContinuedDeposits.toString }," +
      s"${ deposit.storageSpace.toString }"
  }

  private def createDeposit(depositorId: String, state: State) = {
    Deposit(UUID.randomUUID().toString, UUID.randomUUID().toString, Some(true), "FedoraId", depositorId, state, "", DateTime.now().minusDays(3).toString(), 2, 129000, "")
  }

  private def createDeposits = List(
    createDeposit("dans-1", ARCHIVED),
    createDeposit("dans-1", ARCHIVED),
    createDeposit("dans-1", DRAFT),
    createDeposit("dans-1", FINALIZING),
    createDeposit("dans-1", INVALID),
    createDeposit("dans-1", REJECTED),
    createDeposit("dans-1", STALLED),
    createDeposit("dans-1", SUBMITTED),
    createDeposit("dans-1", SUBMITTED),
    createDeposit("dans-1", SUBMITTED),
    createDeposit("dans-1", SUBMITTED), // duplicate deposits are allowed
    createDeposit("dans-1", UNKNOWN),
    createDeposit("dans-1", UNKNOWN),
    createDeposit("dans-1", null), // mapped and added to unknown
    createDeposit("dans-1", null),
  )
}
