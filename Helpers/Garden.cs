using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text;
using clojure.lang;

namespace Garden
{
	public class Garden
	{
		#region data

		private StringBuilder _sb;
		private Dictionary<string, Keyword> _internedKeywords;
		private Dictionary<string, Symbol> _internedSymbols;
		private IDictionary<string, IFn> _taggedReaders;

		#endregion

		#region constructors

		public Garden (IDictionary<string, IFn> taggedReaders = null)
		{
			_taggedReaders = taggedReaders;
			_sb = new StringBuilder();
			_internedKeywords = new Dictionary<string, Keyword>(151);
			_internedSymbols = new Dictionary<string, Symbol>(151);
		}

		#endregion

		#region static methods

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static char PeekChar (TextReader source)
		{
			return (char)source.Peek();
		}

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static char ConsumeChar (TextReader source)
		{
			return (char)source.Read();
		}

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static bool IsWhiteSpace (char c)
		{
			return char.IsWhiteSpace(c) || c == ',' || c == ';';
		}

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static bool IsEmpty (TextReader source)
		{
			return source.Peek() == -1;
		}

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static bool IsSymbolChar (char c)
		{
			return char.IsLetter(c)
				   || c == '.'
				   || c == '*'
				   || c == '+'
				   || c == '!'
				   || c == '-'
				   || c == '_'
				   || c == '?'
				   || c == '$'
				   || c == '%'
				   || c == '&'
				   || c == '='
				   || c == '<'
				   || c == '>'
				   || c == '#'
				   || c == ':';
		}

		[MethodImpl(MethodImplOptions.AggressiveInlining)]
		private static bool IsDelimiter (char c)
		{
			return IsWhiteSpace(c)
				   || c == '{'
				   || c == '}'
				   || c == '('
				   || c == ')'
				   || c == '['
				   || c == ']';
		}

		#endregion

		#region interning

		Keyword InternKeyword (string token)
		{
			//            return Keyword.intern(token);
			Keyword kw;
			if (_internedKeywords.TryGetValue(token, out kw)) {
				return kw;
			}

			kw = Keyword.intern(token);
			_internedKeywords[token] = kw;
			return kw;
		}

		Symbol InternSymbol (string token)
		{
			//            return Symbol.intern(token);
			Symbol sym;
			if (_internedSymbols.TryGetValue(token, out sym)) {
				return sym;
			}

			sym = Symbol.intern(token);
			_internedSymbols[token] = sym;
			return sym;
		}

		#endregion

		#region readers

		public object Read (string source)
		{
			return Read(new StringReader(source));
		}

		public object Read (TextReader source)
		{
			ReadWhiteSpace(source);
			var c = PeekChar(source);
			switch (c) {
			case '"':
				return ReadString(source);
			case '\\':
				return ReadChar(source);
			case ':':
				return ReadKeyword(source);
			case '(':
				return ReadList(source);
			case '[':
				return ReadVector(source);
			case '{':
				return ReadMap(source);
			case '#':
				source.Read();
				switch (PeekChar(source)) {
				case '_':
					Read(source);
					return Read(source);
				case '{':
					return ReadSet(source);
				default:
					if (_taggedReaders != null) {
						var tag = ReadToken(source);
						IFn fn;
						if (_taggedReaders.TryGetValue(tag, out fn)) {
							return fn.invoke(Read(source));
						}

						throw new Exception(string.Format("No tag reader for #{0}", tag));
					}

					break;
				}

				break;
			default:
				if (char.IsNumber(c))
					return ReadNumber(source, (char)0);
				if (c != '+' && c != '-' && c != '.')
					return ReadSymbol(source, (char)0);
				ConsumeChar(source);
				if (char.IsNumber(PeekChar(source))) {
					return ReadNumber(source, c);
				}

				return ReadSymbol(source, c);
			}

			throw new NotImplementedException(string.Format("remaining text: {0}", source.ReadToEnd()));
		}

		private object ReadSymbol (TextReader source, char init)
		{
			var token = ReadToken(source, init);
			if (token == "nil") return null;
			if (token == "true") return true;
			if (token == "false") return false;
			return InternSymbol(token);

		}

		private object ReadNumber (TextReader source, char init)
		{
			bool isDouble = false;
			char c;
			_sb.Clear();
			if (init != (char)0) _sb.Append(init);
			while (!IsDelimiter(PeekChar(source))) {
				c = ConsumeChar(source);
				_sb.Append(c);
				if (c == '.' || c == 'e' || c == 'E')
					isDouble = true;
			}

			return isDouble ? double.Parse(_sb.ToString()) : long.Parse(_sb.ToString());
		}

		private string ReadToken (TextReader source, char init = (char)0)
		{
			_sb.Clear();
			if (init != (char)0) _sb.Append(init);
			while (!IsEmpty(source) && !IsDelimiter(PeekChar(source))) {
				_sb.Append(ConsumeChar(source));
			}

			return _sb.ToString();
		}

		private string ReadString (TextReader source)
		{
			_sb.Clear();
			ConsumeChar(source);
			char c;
			while ((c = ConsumeChar(source)) != '"') {
				if (c == '\\') {
					c = ConsumeChar(source);
					switch (c) {
					case 't':
						_sb.Append('\t');
						break;
					case 'r':
						_sb.Append('\r');
						break;
					case 'n':
						_sb.Append('\n');
						break;
					case '\\':
						_sb.Append('\\');
						break;
					case '"':
						_sb.Append('"');
						break;
					default:
						_sb.Append(c);
						break;
					}
				} else
					_sb.Append(c);
			}

			return _sb.ToString();
		}

		private char ReadChar (TextReader source)
		{
			// TODO support \newline \uXXXX etc
			ConsumeChar(source);
			return ConsumeChar(source);
		}

		private Keyword ReadKeyword (TextReader source)
		{
			ConsumeChar(source);
			return InternKeyword(ReadToken(source));
		}

		private IPersistentList ReadList (TextReader source)
		{
			ConsumeChar(source);
			ReadWhiteSpace(source);
			IPersistentList listBuilder = PersistentList.EMPTY;
			while (PeekChar(source) != ')') {
				listBuilder = (IPersistentList)listBuilder.cons(Read(source));
				ReadWhiteSpace(source);
			}

			ConsumeChar(source);
			return listBuilder;
		}

		private object ReadVector (TextReader source)
		{
			ConsumeChar(source);
			ReadWhiteSpace(source);
			ITransientCollection transientVector = PersistentVector.EMPTY.asTransient();
			while (PeekChar(source) != ']') {
				transientVector = transientVector.conj(Read(source));
				ReadWhiteSpace(source);
			}

			ConsumeChar(source);
			return transientVector.persistent();
		}

		private object ReadMap (TextReader source)
		{
			ConsumeChar(source);
			ReadWhiteSpace(source);
			ITransientMap transientMap = (ITransientMap)PersistentHashMap.EMPTY.asTransient();
			while (PeekChar(source) != '}') {
				var key = Read(source);
				ReadWhiteSpace(source);
				var val = Read(source);
				ReadWhiteSpace(source);
				transientMap = transientMap.assoc(key, val);
			}

			ConsumeChar(source);
			return transientMap.persistent();
		}

		private object ReadSet (TextReader source)
		{
			ConsumeChar(source);
			ReadWhiteSpace(source);
			ITransientCollection transientSet = PersistentHashSet.EMPTY.asTransient();
			while (PeekChar(source) != '}') {
				transientSet = transientSet.conj(Read(source));
				ReadWhiteSpace(source);
			}

			ConsumeChar(source);
			return transientSet.persistent();
		}

		private void ReadTillEndOfLine (TextReader source)
		{
			while (PeekChar(source) != '\n') {
				ConsumeChar(source);
			}
		}

		private void ReadWhiteSpace (TextReader source)
		{
			while (IsWhiteSpace(PeekChar(source))) {
				var c = ConsumeChar(source);
				if (c == ';')
					ReadTillEndOfLine(source);
			}
		}

		public static float Test (string source, int n)
		{
			float nf = n;
			var g = new Garden();
			var sw = new System.Diagnostics.Stopwatch();
			GC.Collect();
			sw.Start();
			object garden = null;
			for (int i = 0; i < n; i++) {
				garden = g.Read(source);
			}

			var end = sw.ElapsedTicks;
			var csTime = end / nf / TimeSpan.TicksPerMillisecond;
			return csTime;
		}

		#endregion
	}
}