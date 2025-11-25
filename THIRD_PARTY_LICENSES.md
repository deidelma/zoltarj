# Third-Party Licenses

This document lists all third-party libraries and tools used by Zoltar, along with their licenses.

## Core Dependencies

### Java Platform
- **Name**: Java Development Kit (JDK)
- **Version**: 25
- **License**: GPL v2 + Classpath Exception
- **URL**: https://openjdk.org/
- **Description**: Java programming language and runtime platform

### JavaFX
- **Name**: OpenJFX (JavaFX)
- **Version**: 21.0.1
- **License**: GPL v2 + Classpath Exception
- **URL**: https://openjfx.io/
- **Description**: UI framework for building desktop applications

### Apache Lucene
- **Name**: Apache Lucene Core
- **Version**: 9.8.0
- **License**: Apache License 2.0
- **URL**: https://lucene.apache.org/
- **Description**: Full-text search engine library

- **Name**: Apache Lucene QueryParser
- **Version**: 9.8.0
- **License**: Apache License 2.0
- **URL**: https://lucene.apache.org/
- **Description**: Query parsing for Lucene

- **Name**: Apache Lucene Analysis Common
- **Version**: 9.8.0
- **License**: Apache License 2.0
- **URL**: https://lucene.apache.org/
- **Description**: Common analysis components for Lucene

### Apache PDFBox
- **Name**: Apache PDFBox
- **Version**: 3.0.0
- **License**: Apache License 2.0
- **URL**: https://pdfbox.apache.org/
- **Description**: Library for working with PDF documents

### SQLite JDBC
- **Name**: SQLite JDBC Driver
- **Version**: 3.43.0.0
- **License**: Apache License 2.0
- **URL**: https://github.com/xerial/sqlite-jdbc
- **Vendor**: Xerial
- **Description**: JDBC driver for SQLite database

### Jackson
- **Name**: Jackson Databind
- **Version**: 2.15.2
- **License**: Apache License 2.0
- **URL**: https://github.com/FasterXML/jackson-databind
- **Description**: JSON processing library

### SLF4J
- **Name**: SLF4J API
- **Version**: 2.0.9
- **License**: MIT License
- **URL**: http://www.slf4j.org/
- **Description**: Simple Logging Facade for Java

### Logback
- **Name**: Logback Classic
- **Version**: 1.4.11
- **License**: Eclipse Public License 1.0 (EPL) / GNU LGPL 2.1
- **URL**: http://logback.qos.ch/
- **Description**: Logging framework for Java

## Testing Dependencies

### JUnit Jupiter
- **Name**: JUnit Jupiter API
- **Version**: 5.10.0
- **License**: Eclipse Public License 2.0
- **URL**: https://junit.org/junit5/
- **Description**: Testing framework for Java

- **Name**: JUnit Jupiter Engine
- **Version**: 5.10.0
- **License**: Eclipse Public License 2.0
- **URL**: https://junit.org/junit5/
- **Description**: Test execution engine for JUnit 5

## Build Tools

### Apache Maven
- **Name**: Apache Maven
- **Version**: 3.9.11
- **License**: Apache License 2.0
- **URL**: https://maven.apache.org/
- **Description**: Build automation and dependency management tool

### Maven Plugins

- **Name**: Maven Compiler Plugin
- **Version**: 3.11.0
- **License**: Apache License 2.0
- **URL**: https://maven.apache.org/plugins/maven-compiler-plugin/
- **Description**: Compiles Java sources

- **Name**: Maven Surefire Plugin
- **Version**: 3.1.2
- **License**: Apache License 2.0
- **URL**: https://maven.apache.org/surefire/maven-surefire-plugin/
- **Description**: Runs unit tests

- **Name**: JavaFX Maven Plugin
- **Version**: 0.0.8
- **License**: Apache License 2.0
- **URL**: https://github.com/openjfx/javafx-maven-plugin
- **Description**: Maven plugin for JavaFX applications

## External APIs (Not Bundled)

### OpenAI API
- **Name**: OpenAI API
- **License**: OpenAI Terms of Use
- **URL**: https://openai.com/
- **Description**: API for embeddings (text-embedding-3-small) and chat completions
- **Note**: Requires API key and is subject to OpenAI's terms and pricing

### PubMed E-utilities
- **Name**: NCBI PubMed E-utilities
- **License**: Public Domain (U.S. Government work)
- **URL**: https://www.ncbi.nlm.nih.gov/books/NBK25501/
- **Description**: Web services for accessing PubMed data
- **Note**: Subject to NCBI's usage guidelines and rate limits

## License Summaries

### Apache License 2.0
Full text: https://www.apache.org/licenses/LICENSE-2.0

A permissive license that allows commercial use, modification, distribution, and patent use. Requires preservation of copyright and license notices. Provides express grant of patent rights from contributors.

**Applies to**: Lucene, PDFBox, SQLite JDBC, Jackson, Maven and plugins

### MIT License
Full text: https://opensource.org/licenses/MIT

A permissive license that allows commercial use, modification, distribution, and private use. Requires preservation of copyright and license notices. Provides no warranty.

**Applies to**: SLF4J

### GPL v2 + Classpath Exception
Full text: https://openjdk.java.net/legal/gplv2+ce.html

The GNU General Public License v2 with the Classpath Exception allows linking to the library without requiring the linking code to be GPL. This is designed for libraries that are part of the Java platform.

**Applies to**: JavaFX (OpenJFX)

### Eclipse Public License 1.0 / 2.0
Full text: https://www.eclipse.org/legal/epl-v10.html
Full text: https://www.eclipse.org/legal/epl-2.0/

A permissive license that allows commercial use, modification, and distribution. Requires preservation of copyright notices. Provides weak copyleft (modifications must be made available).

**Applies to**: Logback (EPL 1.0), JUnit 5 (EPL 2.0)

### GNU LGPL 2.1
Full text: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

Lesser GPL allows linking to LGPL libraries from non-LGPL code. Modified versions of the library must remain LGPL.

**Applies to**: Logback (dual-licensed with EPL 1.0)

## Compliance Notes

1. **Apache License 2.0 Compliance**: All Apache-licensed dependencies require inclusion of their license text and attribution. The NOTICE file (if present) from these libraries must also be included in distributions.

2. **JavaFX (GPL + Classpath Exception)**: The Classpath Exception allows applications to use JavaFX without being subject to GPL requirements. Applications linking to JavaFX do not need to be GPL-licensed.

3. **Logback (Dual License)**: Logback is dual-licensed under EPL 1.0 and LGPL 2.1. Users may choose either license. This project chooses EPL 1.0 for compatibility.

4. **JUnit (Test Only)**: JUnit dependencies are only used during testing and are not distributed with the application.

## Attribution

This software uses the following open-source libraries and tools. We are grateful to their developers and communities:

- The Apache Software Foundation for Lucene, PDFBox, and Maven
- The OpenJFX community for JavaFX
- Taro L. Saito (Xerial) for SQLite JDBC
- FasterXML for Jackson
- QOS.ch for SLF4J and Logback
- The JUnit team for JUnit 5

## Updates

This file was last updated: November 25, 2025

Dependencies and licenses are subject to change. Always verify license information for the specific versions in use by checking the project's `pom.xml` files.

## Contact

For license compliance questions regarding this project, please open an issue in the project repository.
