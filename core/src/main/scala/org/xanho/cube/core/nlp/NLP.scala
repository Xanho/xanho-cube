package org.xanho.cube.core.nlp

import com.seancheatham.graph.Graph
import opennlp.tools.chunker.ChunkerME
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.doccat.DocumentCategorizerME
import opennlp.tools.namefind.NameFinderME
import opennlp.tools.parser.ParserFactory
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.sentdetect.SentenceDetectorME
import opennlp.tools.tokenize.TokenizerME

object NLP {

  implicit class TextHelper(text: String) {

    /**
      * Extracts the sentences from the given text.  A sentence is marked by punctuation boundaries.
      *
      * @return A sequence of sentence strings
      */
    def sentences: Vector[String] =
      new SentenceDetectorME(Model.sentenceModel)
        .sentDetect(text)
        .toVector

    /**
      * Extracts the tokens from the given text.  A token can be a word, number, punctuation, etc.
      *
      * @return A sequence of token strings
      */
    def tokens: Vector[String] =
      new TokenizerME(Model.tokenizerModel)
        .tokenize(text)
        .toVector

    /**
      * Returns a sequence of named entities, as well as their types, within the text
      *
      * @example "Bob is a carpenter" -> Vector((Bob, person))
      * @return A sequence of tuples: Vector((Entity Name, Entity Type))
      */
    def names: Vector[(String, String)] = {
      val tokens =
        text.tokens
      new NameFinderME(Model.tokenNameFinderModel)
        .find(tokens.toArray)
        .toVector
        .map(span =>
          (tokens.slice(span.getStart, span.getEnd).mkString(" "), span.getType)
        )
    }

    /**
      * Categorizes the given text
      *
      * @return The category of the text, as defined by the model
      */
    def categorize: String = {
      val categorizer =
        new DocumentCategorizerME(Model.doccatModel)
      categorizer.getBestCategory(
        categorizer.categorize(text)
      )
    }

    /**
      * Tags the given text, providing the part of speech for each token.  The part of speech is paired with a "certainty"
      * value, indicating how sure the algorithm is about its choice for the tag.
      *
      * @return A sequence of tuples: Vector((Token, (Part of Speech, Certainty)))
      */
    def tag: Vector[(String, PartOfSpeech)] = {
      val tagger =
        new POSTaggerME(Model.posModel)
      val tokens =
        text.tokens
      val tags =
        tagger.tag(tokens.toArray)
      tokens.zip(tags.toVector.map(PartOfSpeech.apply))
    }

    /**
      * Chunks the text
      *
      * TODO: ???
      *
      * @return ???
      */
    def chunks: Vector[Vector[String]] = {
      import scala.collection.JavaConverters._
      val chunker =
        new ChunkerME(Model.chunkerModel)
      chunker.topKSequences(text.tokens.toArray, text.tag.map(_._2.openNlpTag).toArray)
        .toVector
        .map(_.getOutcomes.asScala.toVector)
    }

    /**
      * Constructs a parse tree, represented as a graph
      *
      * TODO: ???
      *
      * @return A tree, represented as a Graph
      */
    def parse: Graph = {
      val parser =
        ParserFactory.create(Model.parserModel)
      ParserTool.parseLine(text, parser, 1)
      ???
    }

  }

}
