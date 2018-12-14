/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.gobra

import java.io.File

import ch.qos.logback.classic.Logger
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.LoggerFactory
import viper.gobra.backend.ViperVerifier
import viper.gobra.frontend.{Config, Enricher, Parser, TypeChecker}
import viper.gobra.reporting.{BackTranslator, VerifierResult}
import viper.gobra.translator.Translator

object GoVerifier {
  val copyright = "(c) Copyright ETH Zurich 2012 - 2018"
}

trait GoVerifier {

  def name: String = {
    this.getClass.getSimpleName
  }

  def verify(config: Config): VerifierResult = {

    // set log level from config
    {
      // TODO: check logger setup, maybe use multiple logger if required
      val packageLogger = LoggerFactory.getLogger(this.getClass.getPackage.getName).asInstanceOf[Logger]
      packageLogger.setLevel(config.logLevel())
    }

    verify(config.inputFile(), config)
  }

  protected[this] def verify(file: File, config: Config): VerifierResult
}

class Gobra extends GoVerifier {

  override def verify(file: File, config: Config): VerifierResult = {

    val result = for {
      parsedProgram <- Parser.parse(file)
      typeInfo <- TypeChecker.check(parsedProgram)
      program = Enricher.enrich(parsedProgram, typeInfo)
      viperTask = Translator.translate(program)
      verifierResult = ViperVerifier.verify(viperTask)
    } yield BackTranslator.backTranslate(verifierResult)

    result.fold(VerifierResult.Failure, identity)
  }
}

class GobraFrontend extends StrictLogging {

  def createVerifier(config: Config): GoVerifier = {
    new Gobra
  }
}

object GobraRunner extends GobraFrontend {
  def main(args: Array[String]): Unit = {
    val config= new Config(args)
    val verifier= createVerifier(config)
    val result= verifier.verify(config)

    result match {
      case VerifierResult.Success =>
        logger.info(s"${verifier.name} found no errors")
      case VerifierResult.Failure(errors) =>
        logger.error(s"${verifier.name} has found ${errors.length} error(s):")
        errors foreach (e => logger.error(s"\t${e.formattedMessage}"))
    }
  }
}