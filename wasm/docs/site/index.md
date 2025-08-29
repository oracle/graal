---
layout: base
---
<section>
  <div>
    <div class="hi">
      <div class="container">
        <div class="hi__row">
          <div class="hi__body">
            <h4 class="hi__title">A high-performance embeddable WebAssembly runtime for Java</h4>
            <div class="hi__buttons">
              <a href="#getting-started" class="btn btn-primary">Quickstart</a>
              <a href="#demos" class="btn btn-primary">Demos</a>
            </div>
          </div>
          <div class="hi__image">
            <img src="{{ '/assets/img/home/webassembly-logo.svg' | relative_url }}" alt="WebAssembly icon">
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<!-- Benefits -->
<section class="content-section">
  <div class="wrapper">
    <div class="langbenefits">
      <div class="container">
        <h3 class="langpage__title-02">Benefits</h3>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/container-icon.svg" | relative_url }}' alt="access icon">
            </div>
            <div class="langbenefits__title">
              <h4>WebAssembly for Java</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="#getting-started">Load and use Wasm modules</a> and functions directly in Java</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/compatibility.svg" | relative_url }}' alt="compatibility icon">
            </div>
            <div class="langbenefits__title">
              <h4>WebAssembly 1.0 Support</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Full WebAssembly 1.0 compatibility and support for many <a href="https://webassembly.org/features/" target="_blank">feature extensions</a>, including WASI</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/speed-icon.svg" | relative_url }}' alt="speed icon">
            </div>
            <div class="langbenefits__title">
              <h4>Portable Native Extensions</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalwasm/graalwasm-embed-c-code-guide/" target="_blank">Integrate C</a>, C++, Rust, and Go libraries using Wasm as an alternative to JNI or FFM API</h5>
            </div>
          </div>
        </div>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/js-integration-icon.svg" | relative_url }}' alt="JavaScript integration icon">
            </div>
            <div class="langbenefits__title">
              <h4>JavaScript integration</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Simplifies use of WebAssembly modules with <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalwasm/graalwasm-spring-boot-photon/" target="_blank">JavaScript bindings</a></h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/speed-icon.svg" | relative_url }}' alt="speed icon">
            </div>
            <div class="langbenefits__title">
              <h4>Fastest Wasm on the JVM</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="https://www.graalvm.org/latest/reference-manual/java/compiler/">Graal JIT</a> compiles Wasm for native code speed</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/coffee-beans-icon.svg" | relative_url }}' alt="coffee beans icon">
            </div>
            <div class="langbenefits__title">
              <h4>100% Java</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Written in <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/wasm" target="_blank">pure Java</a> with zero native dependencies</h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<!-- Get Started -->
<section class="content-section languages__back">
  <div class="wrapper">
    <div class="languages__example">
      <div class="container">
        <h3 id="getting-started" class="langstarter__title">How to Get Started</h3>
        <div class="langpage__benefits-text">
          <h5>You have the option to extend your Java application with WebAssembly, or go straight to the <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalwasm/graalwasm-starter/" target="_blank">starter project</a></h5>
        </div>
        <div class="languages__example-card">
          <div class="language__example-subtitle-mobile">
            <h4>1. Add GraalWasm as a dependency from <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/wasm" target="_blank">Maven Central</a></h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__example-subtitle">
            <h4>1. Add GraalWasm as a dependency from <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/wasm" target="_blank">Maven Central</a></h4>
          </div>
              {%- highlight xml -%}
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>polyglot</artifactId>
  <version>{{ site.language_version }}</version>
</dependency>
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>wasm</artifactId>
  <version>{{ site.language_version }}</version>
  <type>pom</type>
</dependency>
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Maven icon" src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__text-secondary">
            <h4>or</h4>
          </div>
              {%- highlight groovy -%}
implementation("org.graalvm.polyglot:polyglot:{{ site.language_version }}")
implementation("org.graalvm.polyglot:wasm:{{ site.language_version }}")
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Gradle icon" src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="language__example-subtitle-mobile">
            <h4>2. Create a WebAssembly module, for example with <a href="https://webassembly.github.io/wabt/demo/wat2wasm/" target="_blank">wat2wasm</a></h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__example-subtitle">
            <h4>2. Create a WebAssembly module, for example with <a href="https://webassembly.github.io/wabt/demo/wat2wasm/" target="_blank">wat2wasm</a></h4>
          </div>
              {%- highlight wat -%}
;; wat2wasm add-two.wat -o add-two.wasm
(module
  (func (export "addTwo") (param i32 i32) (result i32)
    local.get 0
    local.get 1
    i32.add
  )
)
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="WebAssembly icon" src='{{ "/assets/img/logos/webassembly-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="language__example-subtitle-mobile">
            <h4>3. Embed the Wasm module in Java</h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
              <div class="language__example-subtitle">
                <h4>3. Embed the Wasm module in Java</h4>
              </div>
              <div class="tabs-container">
                <ul class="nav nav-tabs">
                  <li><a href="#" data-bs-toggle="tab" data-bs-target="#version25" class="nav-link active">≥ 25.0</a></li>
                  <li><a href="#" data-bs-toggle="tab" data-bs-target="#version24" class="nav-link">≤ 24.2</a></li>
                </ul>
                <div class="tab-content">
                  <div id="version25" class="tab-pane fade show active">
{% highlight java %}
import java.net.URL;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

try (Context context = Context.create()) {
    URL wasmFile = Main.class.getResource("add-two.wasm");
    Value mainModule = context.eval(Source.newBuilder("wasm", wasmFile).build());
    Value mainInstance = mainModule.newInstance();
    Value addTwo = mainInstance.getMember("exports").getMember("addTwo");
    System.out.println("addTwo(40, 2) = " + addTwo.execute(40, 2));
}
{% endhighlight %}
                  </div>
                  <div id="version24" class="tab-pane fade">
{% highlight java %}
import java.net.URL;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

try (Context context = Context.create()) {
    URL wasmFile = Main.class.getResource("add-two.wasm");
    String moduleName = "main";
    context.eval(Source.newBuilder("wasm", wasmFile).name(moduleName).build());
    Value addTwo = context.getBindings("wasm").getMember(moduleName).getMember("addTwo");
    System.out.println("addTwo(40, 2) = " + addTwo.execute(40, 2));
}
{% endhighlight %}
                  </div>
                </div>
              </div>
            </div><!-- languages__snippet -->
            <div class="example-logo-box">
              <img alt="Java icon" src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
<!-- Demos -->
<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 id="demos" class="truffle__subtitle">Demos</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalwasm/graalwasm-micronaut-photon/" target="_blank">
                <div class="guides__topics">Embed Photon Image Processing Library with GraalWasm in Micronaut</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalwasm/graalwasm-spring-boot-photon/" target="_blank">
                <div class="guides__topics">Embed Photon Image Processing Library with GraalWasm in Spring Boot</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalwasm/graalwasm-embed-c-code-guide/" target="_blank">
                <div class="guides__topics">Embed C in Java Using GraalWasm</div>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>


<!-- Learn More -->
<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 id="demos" class="truffle__subtitle">Learn More</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://www.youtube.com/watch?v=Z2SWSIThHXY" target="_blank">
                <div class="guides__topics">Video: GraalWasm at Wasm I/O 2025</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://medium.com/graalvm/announcing-graalwasm-a-webassembly-engine-in-graalvm-25cd0400a7f2" target="_blank">
                <div class="guides__topics">Blog: Announcing GraalWasm — a WebAssembly engine in GraalVM</div>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
