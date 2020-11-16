//package tm.hillary
//
//import tm.test.BaseSpec
//import java.util.regex.Pattern
//import scala.annotation.tailrec
//import tm.text.Preprocessor
//import tm.text.StopWords
//import tm.text.DataConverter
//import tm.text.NGram
//import scalaz.Scalaz._
//
//class ConvertSpec extends BaseSpec {
//  import scala.language.implicitConversions
//  implicit def stringToNGram(s: String) = NGram.fromConcatenatedString(s)
//
//  import Preprocessor._
//  implicit val stopwords = StopWords.implicits.default
//
//  trait DictionaryFrom2ndEmail {
//    val dictionary: Set[NGram] =
//      Set("thursday", "aiding", "docx", "hillary",
//        "libya", "march", "memo", "qaddafi", "syria", "hrc-memo",
//        "syria-aid", "syria-aid-libya")
//  }
//
//  trait Words {
//    val words: List[NGram] = List("aiding", "docx", "hillary")
//    val counts = List(3, 2, 1)
//    def wordCounts = words.zip(counts).toMap
//    def singleCounts = words.map(w => Map(w -> 1))
//  }
//
//  describe("The counts of the three words") {
//    they("should add up correctly") {
//      new Words {
//        val aidingSingle = singleCounts(0)
//        val aidingCount1 = aidingSingle |+| aidingSingle
//        aidingCount1.size should equal(1)
//        aidingCount1("aiding") should equal(2)
//      }
//    }
//  }
//
//  describe("Hillary Emails") {
//    describe("The second email") {
//      trait SecondEmail extends TestEmails {
//        Given("The second email")
//        val email = bodies.drop(1).head
//      }
//
//      it("should allow the number of words to be counted correctly") {
//        new SecondEmail {
//          When("the words are counted")
//          val counts = tokenizeAndCount(email)
//
//          Then("there should be 13 distinct non-stop-words")
//          counts.size should equal(13)
//
//          And("The word aid should have 3 occurences")
//          counts("aid") should equal(3)
//        }
//      }
//
//      it("should allow n-grams (n=1,2,3) to be found properly") {
//        new SecondEmail {
//          When("the words are counted")
//          val counts = tokenizeAndCount(email, 3)
//
//          Then("there should be 44 distinct n-grams")
//          counts.size should equal(44)
//
//          And("The unigram aiding should have 3 occurences")
//          counts("aid") should equal(3)
//
//          And("The bigram syria-aiding should have 3 occurences")
//          counts("syria-aid") should equal(3)
//
//          And("The trigram hrc-memo-syria should have 2 occurences")
//          counts("hrc-memo-syria") should equal(2)
//        }
//      }
//
//      it("should produce tokens properly without constituent tokens") {
//        new SecondEmail with DictionaryFrom2ndEmail {
//          When("the tokens are produced and constituent tokens are removed")
//          val words = tokenizeAndRemoveStopWords(email).map(NGram.apply)
//
//          //                    val tokens1 = tokenizeWithoutConstituentTokens(
//          //                        words, dictionary.contains, 1)
//          //                    val tokens2 = tokenizeWithoutConstituentTokens(
//          //                        words, dictionary.contains, 2)
//
//// // the test is commented because replaceConstituentTokensByNGrams now tries to
//// // concatenate tokens
////          Then("The token list containing 1-grams should be found correctly")
////          val tokens1 = replaceConstituentTokensByNGrams(
////            words, dictionary.contains(_), 1)
////          tokens1 should contain theSameElementsAs Vector(
////            "thursday", "march", "latest", "syria",
////            "aid", "qaddafi", "sid",
////            "hrc", "memo", "syria", "aid", "libya", "docx", "hrc",
////            "memo", "syria", "aid", "libya", "_030311_docx",
////            "march", "hillary")
////            .map(NGram(_))
//
//          Then("The tokens list containing 1-grams and 2-grams should be correct")
//          val tokens2 = replaceConstituentTokensByNGrams(
//            words, dictionary.contains(_))
//          tokens2 should contain theSameElementsAs Vector(
//            "thursday", "march", "latest", "syria-aid", "qaddafi",
//            "sid", "hrc-memo", "syria-aid", "libya", "docx",
//            "hrc-memo", "syria-aid", "libya", "_030311_docx",
//            "march", "hillary")
//            .map(NGram.fromConcatenatedString)
//
//          val tokens3 = replaceConstituentTokensByNGrams(
//            tokens2, dictionary.contains(_))
//          tokens3 should contain theSameElementsAs Vector(
//            "thursday", "march", "latest", "syria-aid", "qaddafi",
//            "sid", "hrc-memo", "syria-aid-libya", "docx",
//            "hrc-memo", "syria-aid-libya", "_030311_docx",
//            "march", "hillary")
//            .map(NGram.fromConcatenatedString)
//        }
//      }
//    }
//
//    describe("The first 10 emails") {
//      they("should allow number of words to be counted correctly") {
//        new TestEmails {
//          Given("The first 10 emails")
//          val countsByEmails = countWordsInEmails(10)
//
//          When("the term frequencies are computed")
//          val counts = sumWordCounts(countsByEmails)
//
//          checkNumberOfWords(Then)(counts, 84)
//          checkWordOccurence(And)(counts, "aid", 7)
//          checkWordOccurence(And)(counts, "libya", 8)
//        }
//      }
//
//      they("should allow document frequencies to be computed correctly") {
//        new TestEmails {
//          Given("The first 10 emails")
//          val countsByEmails = countWordsInEmails(10)
//
//          When("the document frequencies are computed")
//          val documentFrequencies =
//            computeDocumentFrequencies(countsByEmails)
//
//          checkNumberOfWords(Then)(documentFrequencies, 84)
//
//          checkDocumentFrequency(And)(documentFrequencies, "aid", 3)
//          checkDocumentFrequency(And)(documentFrequencies, "libya", 5)
//        }
//      }
//
//      they("should allow tf-idf to be computed correctly") {
//        new TestEmails {
//          Given("The first 10 emails")
//          val countsByEmails = countWordsInEmails(10)
//
//          When("tf-idf are computed")
//          val tfidf = computeTfIdf(countsByEmails)
//
//          checkNumberOfWords(Then)(tfidf, 84)
//
//          checkTfIdf(And)(tfidf, "aid", 8.4278)
//          checkTfIdf(And)(tfidf, "libya", 5.5452)
//        }
//      }
//
//      they("should allow correct selection of words over 5 occurrences") {
//        new TestEmails {
//          Given("The first 10 emails")
//          val countsByEmails = countWordsInEmails(10)
//
//          When("The dictionary is built")
//          val dictionary = buildDictionary(countsByEmails)
//            .filter(_.tf > 5)
//
//          val tfidf = dictionary.getMap(_.tfidf)
//          checkNumberOfWords(Then)(tfidf, 3)
//
//          checkTfIdf(And)(tfidf, "aid", 8.4278)
//          checkTfIdf(And)(tfidf, "libya", 5.5452)
//
//          And("The word anti is filtered out")
//          tfidf.contains("anti") should be(false)
//        }
//      }
//
//      they("should allow the proper building of bow representation") {
//        new TestEmails {
//          Given("The first 10 emails")
//          val countsByEmails = countWordsInEmails(10)
//
//          When("The data is converted to bow")
//          val dictionary = buildDictionary(countsByEmails).filter(_.tf > 5)
//          val bow = tm.util.Data.fromDictionaryAndTokenCounts(dictionary, countsByEmails)
//
//          Then("The words should be aid, syria, and libya")
//          dictionary.words should contain theSameElementsAs Vector("aid", "syria", "libya")
//
//          And("The first and third email should contain exactly three zero counts")
//          bow.instances(0).denseValues(3) should contain theSameElementsAs Vector(0.0, 0.0, 0.0)
//          bow.instances(2).denseValues(3) should contain theSameElementsAs Vector(0.0, 0.0, 0.0)
//
//          And("The second email should contain correct counts")
//          bow.instances(1).denseValues(3) should contain theSameElementsAs Vector(3.0, 3.0, 2.0)
//
//          And("The bow should have correct number of instances")
//          bow.size should equal(10)
//        }
//      }
//    }
//  }
//
//  describe("Hillary Emails") {
//    they("should contain only proper characters after preprocessing") {
//      new TestEmails {
//        Given("all emails")
//        When("the emails are preprocessed and are converted to words")
//        val words = super.bodies.flatMap(_.split("\\s+")).toSet
//
//        Then("The words should contain only alphanumeric characters or underscores")
//        words.filter(_.matches(".*[^\\p{Alnum}_]+.*")) shouldBe empty
//      }
//    }
//  }
//
//  def findAllPattern(regex: String): (String) => Seq[String] = {
//    (input: String) =>
//      {
//        val matcher = Pattern.compile(regex).matcher(input)
//
//        @tailrec
//        def rec(matches: List[String]): List[String] =
//          if (matcher.find())
//            rec(matcher.group() :: matches)
//          else
//            matches
//
//        rec(List.empty)
//      }
//  }
//
//  def checkNumberOfWords(informer: (String) => Unit)(map: Map[NGram, _], size: Int) = {
//    informer(s"there should be ${size} distinct non-stop-words")
//    map.size should equal(size)
//  }
//
//  def checkDocumentFrequency(informer: (String) => Unit)(
//    df: Map[NGram, Int], word: String, frequency: Int) = {
//    informer(s"The word ${word} should have appeared in ${frequency} documents")
//    df(NGram(word)) should equal(frequency)
//  }
//
//  def checkWordOccurence(informer: (String) => Unit)(
//    counts: Map[NGram, Int], word: String, count: Int) = {
//    informer(s"The word ${word} should have ${count} occurences")
//    counts(NGram(word)) should equal(count)
//  }
//
//  def checkTfIdf(informer: (String) => Unit)(
//    tfidf: Map[NGram, Double], word: String, expected: Double) = {
//    informer(s"The tf-idf of word ${word} should be correct")
//    tfidf(NGram(word)) should equal(expected +- 5e-5)
//  }
//}