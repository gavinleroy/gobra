/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.gobra.frontend

import java.io.File
import java.nio.file.Files

import ch.qos.logback.classic.Level
import com.typesafe.scalalogging.StrictLogging
import org.rogach.scallop.{ScallopConf, ScallopOption, Util, singleArgConverter}

class Config(arguments: Seq[String])
    extends ScallopConf(arguments)
    with StrictLogging {

  /**
    * Prologue
    */
  /**
    * Command-line options
    */
  val inputFile: ScallopOption[File] = opt[File](
    name = "input",
    descr = "Go program to verify is read from this file"
  )(singleArgConverter(arg => new File(arg)))

  val logLevel: ScallopOption[Level] = opt[Level](
    name = "logLevel",
    descr =
      "One of the log levels ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF (default: OFF)",
    default = Some(Level.INFO),
    noshort = true
  )(singleArgConverter(arg => Level.toLevel(arg.toUpperCase)))

  /**
    * Exception handling
    */
  /**
    * Epilogue
    */

  /** Argument Dependencies */
  requireOne(inputFile)

  /** File Validation */
  def validateFileIsReadable(fileOption: ScallopOption[File]) = addValidation {
    fileOption.toOption
      .map(file => {
        if (!Files.isReadable(file.toPath)) Left(Util.format("File '%s' is not readable", file))
        else Right(())
      })
      .getOrElse(Right(()))
  }

  validateFileExists(inputFile)
  validateFileIsFile(inputFile)
  validateFileIsReadable(inputFile)

  verify()

}