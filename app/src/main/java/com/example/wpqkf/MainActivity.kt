package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager

class MainActivity : AppCompatActivity() {

    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnManage: Button
    private lateinit var mBtnProfile4: ImageButton
    private lateinit var database: DatabaseReference
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private lateinit var postList: ArrayList<Post>

    private lateinit var allPostsList: ArrayList<Post> // 전체 원본 데이터
    private lateinit var categories: ArrayList<String>
    private lateinit var filterAdapter: ArrayAdapter<String>

    private fun readPostsFromDatabase() {
        // 파이어베이스의 "posts" 경로를 실시간 감지
        mDatabaseRef.child("posts").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 데이터가 바뀔 때마다 상자를 한 번 비우기(중복 방지)
                allPostsList.clear()
                // posts 폴더 안의 모든 글들을 하나씩 꺼내옴
                for (postSnapshot in snapshot.children) {
                    val post = postSnapshot.getValue(Post::class.java)
                    if (post != null) {
                        // *********** 상세화면 갈 때 키를 쓸거임
                        // 파이어베이스가 생성한 고유 키값(가장 상위 폴더 이름)을 uid나 별도 변수에 박아줌
                        //  post 객체의 uid 변수에 postSnapshot.key를 임시로 대입
                        val updatedPost = post.copy(uid = postSnapshot.key)

                        allPostsList.add(updatedPost)
                    }
                }
                val currentFilter = findViewById<Spinner>(R.id.spinner_filter).selectedItem.toString()
                filterPosts(currentFilter)
            }

            override fun onCancelled(error: DatabaseError) {
                // 실패했을 때 로그 찍기
                android.util.Log.e("FirebaseError", "데이터 읽기 실패: ${error.message}")
            }
        })
    }

    private fun filterPosts(category: String) {
        postList.clear()
        if (category == "전체"){
            postList.addAll(allPostsList)
        } else {
            for (post in allPostsList){
                if (post.category == category) {
                    postList.add(post)
                }
            }
        }
        postAdapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef = FirebaseDatabase.getInstance().reference

        categories = arrayListOf("전체")
        allPostsList = arrayListOf()
        postList = arrayListOf()

        val spinnerFilter: Spinner = findViewById(R.id.spinner_filter)
        filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,categories)
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = filterAdapter
        mDatabaseRef.child("categories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempSelected = spinnerFilter.selectedItem?.toString() ?: "전체"
                categories.clear()
                categories.add("전체")

                for (catSnapshot in snapshot.children) {
                    val cat = catSnapshot.getValue(String::class.java)
                    if (cat != null) categories.add(cat)
                }
                filterAdapter.notifyDataSetChanged()

                // 선택 상태 유지
                val index = categories.indexOf(tempSelected)
                spinnerFilter.setSelection(if (index != -1) index else 0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val selected = categories[position]
                filterPosts(selected)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }
        val firebaseUser = mFirebaseAuth.currentUser



        //내비게이션바 작업-------------------------
        mBtnSchedule4 = findViewById(R.id.schedule)
        mBtnRecord4 = findViewById(R.id.record)
        mBtnHome4 = findViewById(R.id.home)
        mBtnAi4 = findViewById(R.id.ai)
        mBtnProfile4 = findViewById(R.id.profile)
        mBtnManage = findViewById(R.id.manage)
        mBtnManage.visibility = View.GONE

        mBtnHome4.setOnClickListener {

        }
        mBtnSchedule4.setOnClickListener {
            val intent = Intent(this@MainActivity, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord4.setOnClickListener {
            val intent = Intent(this@MainActivity, record::class.java)

            startActivity(intent)
            finish()
        }
        mBtnProfile4.setOnClickListener {
            val intent = Intent(this@MainActivity, profile::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi4.setOnClickListener {
            val intent = Intent(this@MainActivity, ai::class.java)
            startActivity(intent)
            finish()
        }
        //내비게이션 바 작업 완---------------------------

        //read 기능 구현ㄱ
        recyclerView = findViewById(R.id.recyclerViewPosts) // main.xml에 만든 RecyclerView ID
        recyclerView.layoutManager = LinearLayoutManager(this) // 한 줄씩 세로로 쌓기

        recyclerView.setHasFixedSize(true)

        // 3. 빈 상자를 만들고 어댑터에 주입한 뒤, 리사이클러뷰에 장착 (recyclerview가 실시간으로, 스크롤 가능하게 게시글을 보여줌)

        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        // firebase에서 데이터 긁어오기
        readPostsFromDatabase()

        //만약 접속한 사람이 developer라면, 게시물을 올릴 수 있도록 설정 (버튼이 보이게)------------------

        if (firebaseUser != null) {
            mDatabaseRef.child("UserAccount").child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                //정보를 가져오는데 성공하면 자동으로 호출되는 함수
                override fun onDataChange(snapshot: DataSnapshot) {
                    //가져온 정보를 변수 안에 형식에 맞게 가져오기
                    val account = snapshot.getValue(user.UserAccount::class.java)
                    val email = account?.emailId?.toString() ?: mFirebaseAuth.currentUser?.email
                    val isdeveloper = (email == "h_s_posan25_1219@dge.go.kr")
                    val isdeveloperfk = (email == "guest@gmail.com")
                    if (isdeveloper) {
                        mBtnManage.visibility = View.VISIBLE
                    } else {
                        mBtnManage.visibility = View.GONE
                    }
                    if (isdeveloperfk) {
                        mBtnManage.visibility = View.VISIBLE
                    } else {
                        mBtnManage.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
        mBtnManage.setOnClickListener {
            val intent = Intent(this@MainActivity, manage_home::class.java)
            startActivity(intent)
        }
    }
    inner class PostAdapter(private val postList: ArrayList<Post>)://manage_home.dt에서 올린 Post 객체를 매개변수로 준다
        RecyclerView.Adapter<PostAdapter.PostViewHolder>(){
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): PostViewHolder {
            val view = LayoutInflater.from(p0.context).inflate(R.layout.item_post,p0, false)
            return PostViewHolder(view)
        }
        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val currentPost = postList[position]
            holder.title.text = currentPost.title
            holder.author.text = currentPost.author
            holder.category.text = "[${currentPost.category}]"

            holder.itemView.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, postdt::class.java)
                intent.apply{
                    putExtra("post_key", currentPost.uid)
                    context.startActivity(this)
                }
            }
        }

        override fun getItemCount(): Int {
            return postList.size
        }

        // 어댑터가 사용할 뷰홀더 그릇 클래스
        inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.tvItemTitle)
            val author: TextView = itemView.findViewById(R.id.tvItemAuthor)
            val category: TextView = itemView.findViewById(R.id.tvItemCategory) // 추가

        }
        }
}
