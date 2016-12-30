package org.xanho.cube.core.nlp

sealed abstract class PartOfSpeech {
  def openNlpTag: String
}

object PartOfSpeech {
  def apply(openNlpTag: String): PartOfSpeech =
    ???
}