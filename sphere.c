#include <math.h>
#include <stdio.h>

// This is intentionally way too high.
const int kStacks = 150;
const int kSlices = 100;

typedef struct Point_ {
  float x, y, z;
  float nx, ny, nz;
  float u, v;
} Point;

void emit(Point* p, FILE* f) {
  fwrite(p, sizeof(Point), 1, f);
}

int main() {
  // Points
  FILE* point_file = fopen("points_3f3f2f.jet", "wb");
  const unsigned int kVerticesSize = (kStacks * kSlices + 2) * sizeof(Point);
  /*fwrite(&kVerticesSize, sizeof(kVerticesSize), 1, point_file);*/
  Point bottom = { 0, 0, -1, 0, 0, -1, 0, 0 };
  emit(&bottom, point_file);
  for (int i = 0; i < kStacks; ++i) {
    // 1: M_PI / 2
    // 2: M_PI / 3, 2 * M_PI / 3
    float rho = (i + 1) * M_PI / (kStacks + 1);
    for (int j = 0; j < kSlices; ++j) {
      // 1: 0
      // 2: 0, 2 * M_PI
      // 3: 0, M_PI, 2 * M_PI
      // 4: 0, 2/3 * M_PI, 4/3 * M_PI, 2 * M_PI
      float theta = j * 2 * M_PI / (kSlices - 1);

      Point p;
      p.nx = cos(theta) * sin(rho);
      p.ny = sin(theta) * sin(rho);
      p.nz = cos(rho);
      p.x = p.nx;
      p.y = p.ny;
      p.z = 0.9 * p.nz;
      p.u = j / (float)(kSlices - 1);
      p.v = (i + 1) / (float)(kStacks + 1);
      emit(&p, point_file);
    }
  }
  Point top = { 0, 0, 1, 0, 0, 1, 0, 1 };
  emit(&top, point_file);
  fclose(point_file);

  int start = 1;
  // Indices, tristrips
  FILE* tristrip_file = fopen("tristrips.jet", "wb");
  const unsigned int kTristripSize =
      ((kStacks - 1) * kSlices * 2 +
        /*stitch*/
        (kStacks - 1) + (kStacks - 2)
      ) * sizeof(unsigned short);
  /*fwrite(&kTristripSize, sizeof(kTristripSize), 1, tristrip_file);*/
  _Bool stitch = 0;
  for (int i = 0; i < kStacks - 1; ++i) {
    unsigned short slice1 = i * kSlices + start;
    unsigned short slice2 = (i + 1) * kSlices + start;

    if (stitch)
      fwrite(&slice1, sizeof(slice1), 1, tristrip_file);
    fwrite(&slice1, sizeof(slice1), 1, tristrip_file);
    fwrite(&slice2, sizeof(slice2), 1, tristrip_file);

    for (int j = 1; j < kSlices; ++j) {
      slice1++;
      slice2++;
      fwrite(&slice1, sizeof(slice1), 1, tristrip_file);
      fwrite(&slice2, sizeof(slice2), 1, tristrip_file);
    }

    // stitch
    fwrite(&slice2, sizeof(slice2), 1, tristrip_file);

    stitch = 1;
  }
  fclose(tristrip_file);

  // Indices, triangles
  FILE* tri_file = fopen("tris.jet", "wb");
  const unsigned int kTriSize =
      (kStacks - 1) * (kSlices - 1) * 2 * 3 * sizeof(unsigned short);
  /*fwrite(&kTriSize, sizeof(kTriSize), 1, tri_file);*/
  for (int i = 0; i < kStacks - 1; ++i) {
    for (int j = 0; j < kSlices - 1; ++j) {
      unsigned short s1 = i * kSlices + start + j;
      unsigned short s2 = (i + 1) * kSlices + start + j;

      unsigned short s3 = s1 + 1;
      unsigned short s4 = s2 + 1;

      fwrite(&s1, sizeof(s1), 1, tri_file);
      fwrite(&s2, sizeof(s2), 1, tri_file);
      fwrite(&s3, sizeof(s3), 1, tri_file);

      fwrite(&s2, sizeof(s2), 1, tri_file);
      fwrite(&s3, sizeof(s3), 1, tri_file);
      fwrite(&s4, sizeof(s4), 1, tri_file);
    }
  }
  fclose(tri_file);
}

