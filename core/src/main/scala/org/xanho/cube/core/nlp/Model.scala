package org.xanho.cube.core.nlp

import opennlp.tools.chunker.ChunkerModel
import opennlp.tools.doccat.DoccatModel
import opennlp.tools.namefind.TokenNameFinderModel
import opennlp.tools.parser.ParserModel
import opennlp.tools.postag.POSModel
import opennlp.tools.sentdetect.SentenceModel
import opennlp.tools.tokenize.TokenizerModel

object Model {
  
  lazy val sentenceModel: SentenceModel =
    ???
  
  lazy val tokenizerModel: TokenizerModel =
    ???
  
  lazy val tokenNameFinderModel: TokenNameFinderModel =
    ???

  lazy val doccatModel: DoccatModel =
    ???
  
  lazy val posModel: POSModel =
    ???
  
  lazy val chunkerModel: ChunkerModel =
    ???
  
  lazy val parserModel: ParserModel =
    ???

}
