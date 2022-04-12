#
# Copyright (c) 2022, 2022, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
#!/bin/bash

cat << EOF

; Function Attrs: nofree nounwind uwtable
define dso_local void @testVectorGEP_${VECLEN}x${VECTYPE}() local_unnamed_addr #0 {

  %rawPtr = getelementptr i8, i8* bitcast ([5 x i8]* @fmt1 to i8*), i64 0
  %vectorPtr = bitcast i8* %rawPtr to <${VECLEN} x ${VECTYPE}>*
  %vectorPtr0 = getelementptr <${VECLEN} x ${VECTYPE}>, <${VECLEN} x ${VECTYPE}>* %vectorPtr, i64 0
  %vectorPtr1 = getelementptr <${VECLEN} x ${VECTYPE}>, <${VECLEN} x ${VECTYPE}>* %vectorPtr, i64 1
  %vectorPtr2 = getelementptr <${VECLEN} x ${VECTYPE}>, <${VECLEN} x ${VECTYPE}>* %vectorPtr, i64 2
        
  %base = ptrtoint i8* %rawPtr to i64
  %a0 = ptrtoint <${VECLEN} x ${VECTYPE}>* %vectorPtr0 to i64
  %diff0 = sub i64 %a0, %base
  %dummy0 = tail call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([5 x i8], [5 x i8]* @fmt1, i64 0, i64 0), i64 %diff0)
  %a1 = ptrtoint <${VECLEN} x ${VECTYPE}>* %vectorPtr1 to i64
  %diff1 = sub i64 %a1, %base
  %dummy1 = tail call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([5 x i8], [5 x i8]* @fmt1, i64 0, i64 0), i64 %diff1)
  %a2 = ptrtoint <${VECLEN} x ${VECTYPE}>* %vectorPtr2 to i64
  %diff2 = sub i64 %a2, %base
  %dummy2 = tail call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([5 x i8], [5 x i8]* @fmt1, i64 0, i64 0), i64 %diff2)
  
  ret void
}

EOF
